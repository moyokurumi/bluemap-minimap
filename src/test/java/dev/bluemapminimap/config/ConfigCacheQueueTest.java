package dev.bluemapminimap.config;

import dev.bluemapminimap.cache.CacheEntry;
import dev.bluemapminimap.cache.DiskTileCache;
import dev.bluemapminimap.concurrent.DownloadQueue;
import dev.bluemapminimap.model.ResourceValidators;
import dev.bluemapminimap.model.TileAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCacheQueueTest {
    @TempDir
    Path temporary;

    @Test
    void newConfigurationKeepsEightyPixelDefaultAndAllowsFortyPixelMinimum() {
        MinimapConfig config = new MinimapConfig();
        assertEquals(80, config.size);
        config.size = 1;
        config.sanitize();
        assertEquals(40, config.size);
    }

    @Test
    void sizeOptionsMatchSettingsUiOrder() {
        assertEquals(java.util.List.of(40, 50, 60, 70, 80, 90, 100), MinimapConfig.SIZE_OPTIONS);
    }

    @ParameterizedTest
    @ValueSource(ints = {40, 50, 60, 70, 80, 90, 100})
    void selectableSizesSurviveSaveAndClientRestartReload(int size) throws Exception {
        Path file = temporary.resolve("bluemap-minimap-" + size + ".json");
        ConfigStore store = new ConfigStore(file);
        MinimapConfig config = new MinimapConfig();
        config.size = size;
        store.save(config);
        assertEquals(size, new ConfigStore(file).load().size);
    }

    @Test
    void configurationRoundTripKeepsPerServerMappings() throws Exception {
        ConfigStore store = new ConfigStore(temporary.resolve("bluemap-minimap.json"));
        MinimapConfig config = new MinimapConfig();
        ServerProfile profile = new ServerProfile();
        profile.blueMapUrl = "https://map.example/";
        profile.dimensions.put("minecraft:overworld", "overworld");
        config.servers.put("play.example", profile);
        config.size = 192;
        store.save(config);
        MinimapConfig loaded = store.load();
        assertEquals(192, loaded.size);
        assertEquals("overworld", loaded.profileFor("PLAY.EXAMPLE:25565").dimensions.get("minecraft:overworld"));
    }

    @Test
    void rcOneConfigurationMigratesToSafeRcTwoDefaults() throws Exception {
        Path file = temporary.resolve("bluemap-minimap.json");
        Files.writeString(file, "{\"enabled\":true,\"size\":112,\"servers\":{}}", StandardCharsets.UTF_8);
        MinimapConfig loaded = new ConfigStore(file).load();
        assertEquals(MapOrientation.NORTH_UP, loaded.mapOrientation);
        assertTrue(loaded.showCompass);
        assertTrue(loaded.showCoordinates);
        assertTrue(loaded.autoDiscoverBlueMap);
        assertEquals(112, loaded.size);
    }

    @Test
    void orientationUsesDocumentedLowercaseJsonValues() throws Exception {
        Path file = temporary.resolve("bluemap-minimap.json");
        MinimapConfig config = new MinimapConfig();
        config.mapOrientation = MapOrientation.HEADING_UP;
        new ConfigStore(file).save(config);
        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"mapOrientation\": \"heading_up\""));
        assertEquals(MapOrientation.HEADING_UP, new ConfigStore(file).load().mapOrientation);
    }

    @Test
    void invalidOrientationFallsBackToNorthUp() throws Exception {
        Path file = temporary.resolve("bluemap-minimap.json");
        Files.writeString(file, "{\"mapOrientation\":\"sideways\",\"servers\":{}}", StandardCharsets.UTF_8);
        assertEquals(MapOrientation.NORTH_UP, new ConfigStore(file).load().mapOrientation);
    }

    @Test
    void configCopyRetainsRcTwoDisplaySettings() {
        MinimapConfig config = new MinimapConfig();
        config.mapOrientation = MapOrientation.HEADING_UP;
        config.showCompass = false;
        config.showCoordinates = false;
        config.autoDiscoverBlueMap = false;
        MinimapConfig copy = config.copy();
        assertEquals(MapOrientation.HEADING_UP, copy.mapOrientation);
        assertFalse(copy.showCompass);
        assertFalse(copy.showCoordinates);
        assertFalse(copy.autoDiscoverBlueMap);
    }

    @Test
    void diskCacheRoundTripPreservesBytesAndValidators() throws Exception {
        DiskTileCache cache = new DiskTileCache(temporary.resolve("cache"));
        URI base = URI.create("https://map.example/");
        TileAddress tile = new TileAddress("overworld", -1, 2, 1);
        cache.write(base, tile, new byte[]{1, 2, 3}, new ResourceValidators("etag", "date"));
        CacheEntry loaded = cache.read(base, tile).orElseThrow();
        assertArrayEquals(new byte[]{1, 2, 3}, loaded.bytes());
        assertEquals("etag", loaded.validators().etag());
    }

    @Test
    void boundedQueueSuppressesDuplicatePendingWork() throws Exception {
        try (DownloadQueue queue = new DownloadQueue(1, 1)) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            assertNotNull(queue.submitOnce("same", () -> {
                started.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertNull(queue.submitOnce("same", () -> fail("duplicate task ran")));
            release.countDown();
        }
    }
}
