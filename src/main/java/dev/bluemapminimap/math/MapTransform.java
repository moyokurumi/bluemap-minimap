package dev.bluemapminimap.math;

import dev.bluemapminimap.config.MapOrientation;

public final class MapTransform {
    private MapTransform() {
    }

    public static double normalizeDegrees(double degrees) {
        if (!Double.isFinite(degrees)) return 0.0;
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    /**
     * Rotation applied to north-up X/Z screen coordinates. Minecraft yaw is
     * zero toward +Z (south) and increases toward -X (west).
     */
    public static double mapRotationDegrees(double playerYaw, MapOrientation orientation) {
        if (orientation != MapOrientation.HEADING_UP) return 0.0;
        return normalizeDegrees(180.0 - playerYaw);
    }

    /** Keeps the rc.1 north-up arrow behavior and fixes it upright in heading-up mode. */
    public static double selfArrowRotationDegrees(double playerYaw, MapOrientation orientation) {
        return orientation == MapOrientation.HEADING_UP ? 0.0 : normalizeDegrees(180.0 - playerYaw);
    }

    public static Point worldOffsetToScreen(double deltaX, double deltaZ, double playerYaw,
                                            MapOrientation orientation) {
        if (orientation != MapOrientation.HEADING_UP) return new Point(deltaX, deltaZ);
        double radians = Math.toRadians(mapRotationDegrees(playerYaw, orientation));
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Point(cos * deltaX - sin * deltaZ, sin * deltaX + cos * deltaZ);
    }

    public static Point northIndicatorOffset(int size, double playerYaw, MapOrientation orientation, double inset) {
        double radius = Math.max(0.0, size / 2.0 - inset);
        Point direction = worldOffsetToScreen(0.0, -1.0, playerYaw, orientation);
        double scale = radius / Math.max(Math.abs(direction.x()), Math.abs(direction.y()));
        return new Point(direction.x() * scale, direction.y() * scale);
    }

    public record Point(double x, double y) {
    }
}
