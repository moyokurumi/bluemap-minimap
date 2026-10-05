package dev.bluemapminimap.client;

import dev.bluemapminimap.model.MapDescriptor;
import dev.bluemapminimap.model.RemotePlayer;
import dev.bluemapminimap.model.TileAddress;
import dev.bluemapminimap.net.SseStream;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

final class ActiveSession {
    final long generation;
    final String key;
    final URI base;
    final MapDescriptor map;
    final AtomicBoolean closed = new AtomicBoolean();
    final AtomicBoolean sseConnected = new AtomicBoolean();
    final AtomicBoolean playerPollInFlight = new AtomicBoolean();
    final Set<TileAddress> wantedTiles = ConcurrentHashMap.newKeySet();
    final ConcurrentHashMap<TileAddress, Long> tileEventTimes = new ConcurrentHashMap<>();
    final Set<Future<?>> requests = ConcurrentHashMap.newKeySet();

    volatile TileAddress centerTile;
    volatile List<RemotePlayer> players = List.of();
    volatile SseStream sseStream;
    volatile long lastTileUpdateEpochMillis;
    volatile long lastPlayerPollNanos;
    volatile long nextTileRefreshNanos;

    ActiveSession(long generation, String key, URI base, MapDescriptor map) {
        this.generation = generation;
        this.key = key;
        this.base = base;
        this.map = map;
    }

    void close() {
        closed.set(true);
        sseConnected.set(false);
        SseStream stream = sseStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
        requests.forEach(request -> request.cancel(true));
        requests.clear();
    }
}
