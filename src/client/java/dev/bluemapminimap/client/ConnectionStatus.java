package dev.bluemapminimap.client;

public record ConnectionStatus(
        String mode,
        String mapId,
        String tile,
        int queuedDownloads,
        int activeDownloads,
        int cachedTiles,
        long lastTileUpdateEpochMillis
) {
    public static ConnectionStatus inactive() {
        return new ConnectionStatus("inactive", "-", "-", 0, 0, 0, 0L);
    }
}
