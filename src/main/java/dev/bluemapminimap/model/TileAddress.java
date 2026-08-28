package dev.bluemapminimap.model;

public record TileAddress(String mapId, int x, int z, int lod) {
    public TileAddress {
        if (mapId == null || mapId.isBlank()) throw new IllegalArgumentException("mapId must not be blank");
        if (lod < 1) throw new IllegalArgumentException("lod must be positive");
    }

    public static TileAddress atWorldPosition(String mapId, double worldX, double worldZ, int tileSizeX, int tileSizeZ) {
        int blockX = floorToInt(worldX);
        int blockZ = floorToInt(worldZ);
        return new TileAddress(mapId, Math.floorDiv(blockX, tileSizeX), Math.floorDiv(blockZ, tileSizeZ), 1);
    }

    private static int floorToInt(double value) {
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world coordinate is not finite or is out of range");
        }
        return (int) Math.floor(value);
    }
}
