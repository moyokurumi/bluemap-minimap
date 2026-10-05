package dev.bluemapminimap.math;

import dev.bluemapminimap.config.MapOrientation;
import dev.bluemapminimap.model.TileAddress;

import java.util.LinkedHashSet;
import java.util.Set;

public final class TileCoverage {
    private TileCoverage() {
    }

    public static double sourceHalfExtentBlocks(int displaySize, double blocksPerPixel,
                                                MapOrientation orientation) {
        if (displaySize <= 0 || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0) {
            throw new IllegalArgumentException("invalid viewport dimensions");
        }
        double half = displaySize * blocksPerPixel / 2.0;
        return orientation == MapOrientation.HEADING_UP ? half * Math.sqrt(2.0) : half;
    }

    /**
     * Returns a yaw-independent tile set that covers every possible rotation of
     * the current viewport. A one-tile prefetch margin is preserved.
     */
    public static Set<TileAddress> requiredTiles(String mapId, double playerX, double playerZ,
                                                 int tileSizeX, int tileSizeZ, int displaySize,
                                                 double blocksPerPixel, MapOrientation orientation) {
        TileAddress center = TileAddress.atWorldPosition(mapId, playerX, playerZ, tileSizeX, tileSizeZ);
        double extent = sourceHalfExtentBlocks(displaySize, blocksPerPixel, orientation);
        int minX = Math.min(center.x() - 1, tileCoordinate(playerX - extent, tileSizeX));
        int maxX = Math.max(center.x() + 1, tileCoordinate(playerX + extent, tileSizeX));
        int minZ = Math.min(center.z() - 1, tileCoordinate(playerZ - extent, tileSizeZ));
        int maxZ = Math.max(center.z() + 1, tileCoordinate(playerZ + extent, tileSizeZ));

        Set<TileAddress> result = new LinkedHashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                result.add(new TileAddress(mapId, x, z, 1));
            }
        }
        return Set.copyOf(result);
    }

    private static int tileCoordinate(double coordinate, int tileSize) {
        return TileAddress.atWorldPosition("coverage", coordinate, 0.0, tileSize, 1).x();
    }
}
