package dev.bluemapminimap.model;

import java.net.URI;

public record MapDescriptor(
        String mapId,
        URI mapRoot,
        URI liveRoot,
        int tileSizeX,
        int tileSizeZ,
        int lodFactor,
        int lodCount
) {
    public MapDescriptor {
        if (mapId == null || mapId.isBlank()) throw new IllegalArgumentException("mapId must not be blank");
        if (tileSizeX <= 0 || tileSizeZ <= 0) throw new IllegalArgumentException("tile size must be positive");
        if (lodFactor <= 0 || lodCount <= 0) throw new IllegalArgumentException("invalid low-resolution layout");
    }
}
