package dev.bluemapminimap.client;

import dev.bluemapminimap.cache.CacheEntry;
import dev.bluemapminimap.cache.DiskTileCache;
import dev.bluemapminimap.client.render.ManagedTexture;
import dev.bluemapminimap.client.render.TextureRegistry;
import dev.bluemapminimap.concurrent.DownloadQueue;
import dev.bluemapminimap.config.ConfigStore;
import dev.bluemapminimap.config.MinimapConfig;
import dev.bluemapminimap.config.MoyoServerPolicy;
import dev.bluemapminimap.config.ServerProfile;
import dev.bluemapminimap.model.ClientObservation;
import dev.bluemapminimap.model.FetchResult;
import dev.bluemapminimap.model.MapDescriptor;
import dev.bluemapminimap.model.RemotePlayer;
import dev.bluemapminimap.model.ResourceValidators;
import dev.bluemapminimap.model.TileAddress;
import dev.bluemapminimap.math.TileCoverage;
import dev.bluemapminimap.net.HttpFailure;
import dev.bluemapminimap.net.SafeHttpClient;
import dev.bluemapminimap.net.SseStream;
import dev.bluemapminimap.protocol.BlueMapJson;
import dev.bluemapminimap.protocol.BlueMapUris;
import dev.bluemapminimap.protocol.GlobalSettings;
import dev.bluemapminimap.protocol.PngHeader;
import dev.bluemapminimap.protocol.SseParser;
import dev.bluemapminimap.protocol.TileEvent;
import dev.bluemapminimap.util.RateLimiter;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class BlueMapRuntime implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlueMap Minimap");
    private static final int JSON_LIMIT = 1024 * 1024;
    private static final int PNG_LIMIT = 8 * 1024 * 1024;
    private static final long TILE_EVENT_DEBOUNCE_MILLIS = 500L;
    private static final long CONNECTION_RETRY_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long TILE_RETRY_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final Minecraft minecraft;
    private final ConfigStore configStore;
    private final DiskTileCache diskCache;
    private final TextureRegistry textures;
    private final SafeHttpClient http = new SafeHttpClient();
    private final DownloadQueue tileDownloads = new DownloadQueue(2, 64);
    private final DownloadQueue headDownloads = new DownloadQueue(2, 32);
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor(task -> daemon(task, "BlueMap-Minimap-Metadata"));
    private final ExecutorService sseExecutor = Executors.newSingleThreadExecutor(task -> daemon(task, "BlueMap-Minimap-SSE"));
    private final ScheduledExecutorService pollingExecutor = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "BlueMap-Minimap-Polling"));
    private final AtomicLong generation = new AtomicLong();
    private final RateLimiter errorLimiter = new RateLimiter(Duration.ofSeconds(30));

    private volatile MinimapConfig config;
    private volatile ActiveSession session;
    private volatile String desiredKey = "";
    private volatile ClientObservation latestObservation;
    private volatile long nextConnectionAttemptNanos = Long.MAX_VALUE;
    private volatile Future<?> connectionRequest;
    private volatile boolean closed;

    public BlueMapRuntime(Minecraft minecraft, Path configDirectory) {
        this.minecraft = minecraft;
        this.configStore = new ConfigStore(configDirectory.resolve("bluemap-minimap.json"));
        this.diskCache = new DiskTileCache(configDirectory.resolve("bluemap-minimap-cache"));
        this.textures = new TextureRegistry(minecraft);
        this.config = loadConfigSafely();
        pollingExecutor.scheduleAtFixedRate(this::pollPlayersIfNeeded, 1, 1, TimeUnit.SECONDS);
    }

    public MinimapConfig config() {
        return config;
    }

    /**
     * Applies display settings immediately so the settings screen can provide a
     * live preview. Persistence and the single connection restart are deferred
     * until the user closes the screen.
     */
    public void previewConfig(MinimapConfig updated) {
        updated.sanitize();
        config = updated.copy();
    }

    public void saveConfig(MinimapConfig updated) {
        updated.sanitize();
        try {
            configStore.save(updated);
            config = updated.copy();
            restartForConfigChange();
        } catch (IOException ex) {
            safeError("config-save", "Could not save the minimap configuration", ex);
        }
    }

    public void reloadConfig() {
        config = loadConfigSafely();
        restartForConfigChange();
    }

    public void tick(ClientObservation observation) {
        if (closed) return;
        latestObservation = observation;
        if (observation == null || !config.enabled || !MoyoServerPolicy.supports(observation.serverAddress())) {
            if (!desiredKey.isEmpty()) disconnect();
            return;
        }
        ServerProfile profile = MoyoServerPolicy.profileFor(config, observation.serverAddress());
        String key = MinimapConfig.normalizeServerAddress(observation.serverAddress()) + "|" + observation.dimension()
                + "|" + Integer.toHexString(profile.blueMapUrl.hashCode());
        if (!key.equals(desiredKey) || (session == null && System.nanoTime() >= nextConnectionAttemptNanos)) {
            beginConnection(key, profile, observation);
            return;
        }
        ActiveSession current = session;
        if (current != null && key.equals(current.key)) updateNearbyTiles(current, observation);
    }

    public RuntimeView view() {
        ActiveSession current = session;
        ClientObservation observation = latestObservation;
        if (current == null || observation == null || current.closed.get()
                || !MoyoServerPolicy.supports(observation.serverAddress())) return RuntimeView.empty(config.copy());
        UUID localUuid = minecraft.player == null ? null : minecraft.player.getUUID();
        float liveYaw = minecraft.player == null ? observation.yaw() : minecraft.player.getYRot();
        Map<TileAddress, ManagedTexture> tileSnapshot = textures.tileSnapshot();
        Map<UUID, ManagedTexture> headSnapshot = textures.headSnapshot();
        return new RuntimeView(config.copy(), current.map, observation.x(), observation.z(), liveYaw, localUuid,
                tileSnapshot, headSnapshot, current.players);
    }

    public ConnectionStatus status() {
        ActiveSession current = session;
        if (current == null) return ConnectionStatus.inactive();
        TileAddress center = current.centerTile;
        return new ConnectionStatus(
                current.sseConnected.get() ? "sse" : "polling",
                current.map.mapId(),
                center == null ? "-" : center.x() + "," + center.z(),
                tileDownloads.queuedCount(), tileDownloads.activeCount(), textures.tileCount(), current.lastTileUpdateEpochMillis);
    }

    public void disconnect() {
        desiredKey = "";
        nextConnectionAttemptNanos = Long.MAX_VALUE;
        generation.incrementAndGet();
        Future<?> pending = connectionRequest;
        connectionRequest = null;
        if (pending != null) pending.cancel(true);
        ActiveSession old = session;
        session = null;
        if (old != null) old.close();
        tileDownloads.cancelQueued();
        headDownloads.cancelQueued();
        if (minecraft.isSameThread()) textures.clear();
        else minecraft.execute(textures::clear);
    }

    private void beginConnection(String key, ServerProfile profile, ClientObservation observation) {
        disconnect();
        desiredKey = key;
        long expectedGeneration = generation.get();
        ServerProfile safeProfile = profile.copy();
        connectionRequest = metadataExecutor.submit(() -> initialize(expectedGeneration, key, safeProfile, observation));
    }

    private void initialize(long expectedGeneration, String key, ServerProfile profile, ClientObservation observation) {
        try {
            if (!isCurrent(expectedGeneration, key)) return;
            // Do not contact the web endpoint at all for an unmapped world.
            if (!profile.dimensions.containsKey(observation.dimension())) return;
            URI base = BlueMapUris.validatedBase(profile.blueMapUrl);
            FetchResult globalResponse = http.get(BlueMapUris.rootSettings(base), JSON_LIMIT, ResourceValidators.NONE, "application/json");
            if (globalResponse.status() != FetchResult.Status.OK) throw new IOException("BlueMap settings are unavailable");
            GlobalSettings global = BlueMapJson.parseGlobalSettings(globalResponse.body());
            String mapId = MoyoServerPolicy.mapFor(observation.dimension(), profile, global.maps());
            if (mapId == null) {
                safeError("mapping", "No BlueMap map is configured for the current dimension", null);
                return;
            }
            URI mapRoot = BlueMapUris.mapRoot(base, global.mapDataRoot(), mapId);
            URI liveRoot = BlueMapUris.liveRoot(base, global.liveDataRoot(), mapId);
            FetchResult mapResponse = http.get(BlueMapUris.mapSettings(base, global.mapDataRoot(), mapId), JSON_LIMIT,
                    ResourceValidators.NONE, "application/json");
            if (mapResponse.status() != FetchResult.Status.OK) throw new IOException("BlueMap map settings are unavailable");
            int[] layout = BlueMapJson.parseLowresLayout(mapResponse.body());
            MapDescriptor descriptor = new MapDescriptor(mapId, mapRoot, liveRoot, layout[0], layout[1], layout[2], layout[3]);
            if (!isCurrent(expectedGeneration, key)) return;
            minecraft.execute(() -> {
                // Session installation and disconnect run on the client thread, so
                // a late metadata response cannot resurrect a previous world's HUD.
                if (!isCurrent(expectedGeneration, key)) return;
                ActiveSession created = new ActiveSession(expectedGeneration, key, base, descriptor);
                session = created;
                updateNearbyTiles(created, latestObservation);
                metadataExecutor.execute(() -> fetchPlayers(created));
                sseExecutor.execute(() -> runSse(created));
            });
        } catch (HttpFailure ex) {
            safeError("initialize-" + ex.category(), "BlueMap connection setup failed: " + ex.category(), null);
        } catch (RuntimeException | IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            safeError("initialize", "BlueMap connection setup failed", ex);
        } finally {
            if (isCurrent(expectedGeneration, key)) nextConnectionAttemptNanos = System.nanoTime() + CONNECTION_RETRY_NANOS;
        }
    }

    private void updateNearbyTiles(ActiveSession current, ClientObservation observation) {
        if (observation == null || !isCurrent(current)) return;
        TileAddress center;
        try {
            center = TileAddress.atWorldPosition(current.map.mapId(), observation.x(), observation.z(),
                    current.map.tileSizeX(), current.map.tileSizeZ());
        } catch (IllegalArgumentException ex) {
            return;
        }
        Set<TileAddress> required;
        try {
            required = TileCoverage.requiredTiles(current.map.mapId(), observation.x(), observation.z(),
                    current.map.tileSizeX(), current.map.tileSizeZ(), config.size, config.zoom, config.mapOrientation);
        } catch (IllegalArgumentException ex) {
            return;
        }
        Set<TileAddress> wanted = current.wantedTiles;
        long now = System.nanoTime();
        if (center.equals(current.centerTile) && wanted.equals(required) && now < current.nextTileRefreshNanos) return;
        current.nextTileRefreshNanos = now + TILE_RETRY_NANOS;
        current.centerTile = center;
        wanted.clear();
        wanted.addAll(required);
        for (TileAddress tile : required) {
            requestTile(current, tile, false);
        }
    }

    private void requestTile(ActiveSession current, TileAddress tile, boolean refresh) {
        current.requests.removeIf(Future::isDone);
        String key = current.generation + ":" + tile;
        Future<?> request = tileDownloads.submitOnce(key, () -> downloadTile(current, tile, refresh));
        if (request != null) current.requests.add(request);
    }

    private void downloadTile(ActiveSession current, TileAddress tile, boolean refresh) {
        if (!isCurrent(current)) return;
        Optional<CacheEntry> cached = Optional.empty();
        try {
            cached = diskCache.read(current.base, tile);
            if (cached.isPresent() && !textures.containsTile(tile)) decodeAndInstallTile(current, tile, cached.get().bytes());
            ResourceValidators validators = cached.map(CacheEntry::validators).orElse(ResourceValidators.NONE);
            FetchResult response = http.get(BlueMapUris.lowresTile(current.map.mapRoot(), tile), PNG_LIMIT, validators, "image/png");
            if (response.status() == FetchResult.Status.OK) {
                if (!response.contentType().toLowerCase(java.util.Locale.ROOT).startsWith("image/png")) {
                    throw new IOException("BlueMap tile response is not a PNG image");
                }
                NativeImage image = decodeTile(response.body(), current.map);
                try {
                    diskCache.write(current.base, tile, response.body(), response.validators());
                } catch (IOException ex) {
                    safeError("cache-write", "Could not update the minimap disk cache", ex);
                }
                installDecodedTile(current, tile, image);
            } else if (response.status() == FetchResult.Status.NOT_MODIFIED && cached.isEmpty()) {
                requestTile(current, tile, true);
            }
        } catch (HttpFailure ex) {
            safeError("tile-" + ex.category(), "Tile request failed: " + ex.category(), null);
        } catch (IOException | RuntimeException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            safeError("tile", "Tile processing failed", ex);
        }
    }

    private void decodeAndInstallTile(ActiveSession current, TileAddress tile, byte[] bytes) throws IOException {
        NativeImage image = decodeTile(bytes, current.map);
        installDecodedTile(current, tile, image);
    }

    private NativeImage decodeTile(byte[] bytes, MapDescriptor map) throws IOException {
        PngHeader.validateLowres(bytes, map.tileSizeX(), map.tileSizeZ());
        NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
        int expectedWidth = map.tileSizeX() + 1;
        int expectedHeight = (map.tileSizeZ() + 1) * 2;
        if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
            image.close();
            throw new IOException("BlueMap tile dimensions are invalid");
        }
        return image;
    }

    private void installDecodedTile(ActiveSession current, TileAddress tile, NativeImage image) {
        if (!isCurrent(current)) {
            image.close();
            return;
        }
        minecraft.execute(() -> {
            if (!isCurrent(current)) {
                image.close();
                return;
            }
            textures.installTile(tile, image);
            current.lastTileUpdateEpochMillis = System.currentTimeMillis();
        });
    }

    private void runSse(ActiveSession current) {
        long backoffMillis = 1000L;
        while (isCurrent(current)) {
            try (SseStream stream = http.openSse(current.map.liveRoot().resolve("live/sse"))) {
                current.sseStream = stream;
                current.sseConnected.set(true);
                backoffMillis = 1000L;
                SseParser parser = new SseParser((event, data) -> handleSseEvent(current, event, data));
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream.input(), StandardCharsets.UTF_8))) {
                    String line;
                    while (isCurrent(current) && (line = reader.readLine()) != null) parser.acceptLine(line);
                    parser.finish();
                }
            } catch (HttpFailure ex) {
                safeError("sse-" + ex.category(), "BlueMap live updates unavailable: " + ex.category(), null);
            } catch (IOException | RuntimeException | InterruptedException ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                if (isCurrent(current)) safeError("sse", "BlueMap live update connection was interrupted", ex);
            } finally {
                current.sseConnected.set(false);
                current.sseStream = null;
            }
            if (!waitBackoff(current, backoffMillis)) return;
            backoffMillis = Math.min(30_000L, backoffMillis * 2L);
        }
    }

    private void handleSseEvent(ActiveSession current, String event, String data) {
        if (!isCurrent(current)) return;
        try {
            switch (event) {
                case "tile" -> {
                    TileEvent update = BlueMapJson.parseTileEvent(data);
                    if (update.lod() != 1) return;
                    TileAddress tile = new TileAddress(current.map.mapId(), update.x(), update.z(), 1);
                    if (!current.wantedTiles.contains(tile) && !textures.containsTile(tile)) return;
                    long now = System.currentTimeMillis();
                    Long previous = current.tileEventTimes.put(tile, now);
                    if (previous == null || now - previous >= TILE_EVENT_DEBOUNCE_MILLIS) requestTile(current, tile, true);
                }
                case "player" -> applyPlayers(current, BlueMapJson.parsePlayers(data.getBytes(StandardCharsets.UTF_8)));
                case "marker" -> {
                    // Marker events are consumed so the connection stays compatible; v0.1 has no marker overlay.
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            safeError("sse-event", "Ignored an invalid BlueMap live update", ex);
        }
    }

    private void pollPlayersIfNeeded() {
        ActiveSession current = session;
        if (!isCurrent(current) || current.sseConnected.get()) return;
        long now = System.nanoTime();
        if (now - current.lastPlayerPollNanos < TimeUnit.MILLISECONDS.toNanos(900)) return;
        if (!current.playerPollInFlight.compareAndSet(false, true)) return;
        current.lastPlayerPollNanos = now;
        try {
            metadataExecutor.execute(() -> {
                try {
                    fetchPlayers(current);
                } finally {
                    current.playerPollInFlight.set(false);
                }
            });
        } catch (RejectedExecutionException ex) {
            current.playerPollInFlight.set(false);
        }
    }

    private void fetchPlayers(ActiveSession current) {
        if (!isCurrent(current)) return;
        try {
            FetchResult response = http.get(current.map.liveRoot().resolve("live/players.json"), JSON_LIMIT,
                    ResourceValidators.NONE, "application/json");
            if (response.status() == FetchResult.Status.OK) applyPlayers(current, BlueMapJson.parsePlayers(response.body()));
        } catch (HttpFailure ex) {
            safeError("players-" + ex.category(), "Player update failed: " + ex.category(), null);
        } catch (RuntimeException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            safeError("players", "Player update failed", ex);
        }
    }

    private void applyPlayers(ActiveSession current, List<RemotePlayer> players) {
        if (!isCurrent(current)) return;
        current.players = players.stream().filter(player -> !player.foreign()).toList();
        if (!config.showPlayers) return;
        for (RemotePlayer player : current.players) requestHead(current, player.uuid());
    }

    private void requestHead(ActiveSession current, UUID uuid) {
        current.requests.removeIf(Future::isDone);
        if (textures.headSnapshot().containsKey(uuid)) return;
        Future<?> request = headDownloads.submitOnce(current.generation + ":head:" + uuid, () -> {
            if (!isCurrent(current)) return;
            try {
                FetchResult response = http.get(BlueMapUris.playerHead(current.map.mapRoot(), uuid.toString()),
                        512 * 1024, ResourceValidators.NONE, "image/png");
                if (response.status() != FetchResult.Status.OK) return;
                if (!response.contentType().toLowerCase(java.util.Locale.ROOT).startsWith("image/png")) return;
                NativeImage image = NativeImage.read(new ByteArrayInputStream(response.body()));
                if (image.getWidth() <= 0 || image.getHeight() <= 0 || image.getWidth() > 256 || image.getHeight() > 256) {
                    image.close();
                    return;
                }
                minecraft.execute(() -> {
                    if (isCurrent(current)) textures.installHead(uuid, image);
                    else image.close();
                });
            } catch (HttpFailure ex) {
                safeError("head-" + ex.category(), "Player head request failed: " + ex.category(), null);
            } catch (IOException | RuntimeException | InterruptedException ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                safeError("head", "Player head processing failed", ex);
            }
        });
        if (request != null) current.requests.add(request);
    }

    private boolean waitBackoff(ActiveSession current, long millis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (isCurrent(current) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(Math.min(200L, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isCurrent(current);
    }

    private boolean isCurrent(long expectedGeneration, String key) {
        return !closed && generation.get() == expectedGeneration && key.equals(desiredKey);
    }

    private boolean isCurrent(ActiveSession candidate) {
        return candidate != null && !closed && !candidate.closed.get() && session == candidate && generation.get() == candidate.generation;
    }

    private void restartForConfigChange() {
        ClientObservation observation = latestObservation;
        disconnect();
        if (observation != null) tick(observation);
    }

    private MinimapConfig loadConfigSafely() {
        try {
            return configStore.load();
        } catch (IOException ex) {
            safeError("config-load", "Could not load the minimap configuration; defaults are active", ex);
            return new MinimapConfig();
        }
    }

    private void safeError(String category, String message, Throwable error) {
        if (!errorLimiter.allow(category)) return;
        if (error == null) LOGGER.warn(message);
        else LOGGER.warn("{} ({})", message, error.getClass().getSimpleName());
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        disconnect();
        metadataExecutor.shutdownNow();
        sseExecutor.shutdownNow();
        pollingExecutor.shutdownNow();
        tileDownloads.close();
        headDownloads.close();
        http.close();
    }
}
