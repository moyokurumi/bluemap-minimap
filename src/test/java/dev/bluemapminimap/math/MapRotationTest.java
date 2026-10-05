package dev.bluemapminimap.math;

import dev.bluemapminimap.config.MapOrientation;
import dev.bluemapminimap.model.TileAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MapRotationTest {
    private static final double EPSILON = 1.0e-9;

    @ParameterizedTest
    @CsvSource({
            "0,0", "360,0", "-1,359", "361,1", "720,0", "-721,359"
    })
    void yawNormalization(double input, double expected) {
        assertEquals(expected, MapTransform.normalizeDegrees(input), EPSILON);
    }

    @ParameterizedTest
    @CsvSource({
            "180,0,-1", "0,0,1", "90,1,0", "270,-1,0", "-90,-1,0"
    })
    void northIndicatorFollowsActualNorthInHeadingUp(double yaw, double expectedX, double expectedY) {
        MapTransform.Point direction = MapTransform.worldOffsetToScreen(
                0, -1, yaw, MapOrientation.HEADING_UP);
        assertEquals(expectedX, direction.x(), EPSILON);
        assertEquals(expectedY, direction.y(), EPSILON);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,1", "90,-1,0", "180,0,-1", "270,1,0", "-90,1,0"
    })
    void minecraftHeadingAlwaysTransformsToScreenUp(double yaw, double headingX, double headingZ) {
        MapTransform.Point transformed = MapTransform.worldOffsetToScreen(
                headingX, headingZ, yaw, MapOrientation.HEADING_UP);
        assertEquals(0.0, transformed.x(), EPSILON);
        assertEquals(-1.0, transformed.y(), EPSILON);
    }

    @Test
    void northUpLeavesWorldOffsetsUnchanged() {
        MapTransform.Point point = MapTransform.worldOffsetToScreen(12.5, -7.25, 93,
                MapOrientation.NORTH_UP);
        assertEquals(12.5, point.x(), EPSILON);
        assertEquals(-7.25, point.y(), EPSILON);
    }

    @Test
    void playerMarkersRotateAtNorthHeading() {
        assertPoint(1, -1, MapTransform.worldOffsetToScreen(1, -1, 180, MapOrientation.HEADING_UP));
        assertPoint(-1, 0, MapTransform.worldOffsetToScreen(-1, 0, 180, MapOrientation.HEADING_UP));
    }

    @Test
    void playerMarkersRotateAtSouthHeading() {
        assertPoint(-1, 1, MapTransform.worldOffsetToScreen(1, -1, 0, MapOrientation.HEADING_UP));
        assertPoint(0, -1, MapTransform.worldOffsetToScreen(0, 1, 0, MapOrientation.HEADING_UP));
    }

    @Test
    void playerMarkersRotateAtWestHeading() {
        assertPoint(1, 1, MapTransform.worldOffsetToScreen(1, -1, 90, MapOrientation.HEADING_UP));
        assertPoint(0, -1, MapTransform.worldOffsetToScreen(-1, 0, 90, MapOrientation.HEADING_UP));
    }

    @Test
    void playerMarkersRotateAtEastHeading() {
        assertPoint(-1, -1, MapTransform.worldOffsetToScreen(1, -1, 270, MapOrientation.HEADING_UP));
        assertPoint(0, -1, MapTransform.worldOffsetToScreen(1, 0, 270, MapOrientation.HEADING_UP));
    }

    @Test
    void playerMarkersRotateAtFortyFiveDegrees() {
        MapTransform.Point northeast = MapTransform.worldOffsetToScreen(1, -1, 45,
                MapOrientation.HEADING_UP);
        assertEquals(0.0, northeast.x(), EPSILON);
        assertEquals(Math.sqrt(2.0), northeast.y(), EPSILON);
    }

    @Test
    void headingUpSelfArrowIsFixedAndNorthUpKeepsRcOneRotation() {
        assertEquals(0.0, MapTransform.selfArrowRotationDegrees(37, MapOrientation.HEADING_UP), EPSILON);
        assertEquals(143.0, MapTransform.selfArrowRotationDegrees(37, MapOrientation.NORTH_UP), EPSILON);
    }

    @ParameterizedTest
    @CsvSource({
            "180,0,-32", "0,0,32", "90,32,0", "270,-32,0"
    })
    void compassNorthTouchesCorrectEdge(double yaw, double x, double y) {
        MapTransform.Point point = MapTransform.northIndicatorOffset(80, yaw, MapOrientation.HEADING_UP, 8);
        assertEquals(x, point.x(), EPSILON);
        assertEquals(y, point.y(), EPSILON);
    }

    @Test
    void northUpCompassAlwaysStaysAtTop() {
        assertPoint(0, -32, MapTransform.northIndicatorOffset(80, 73, MapOrientation.NORTH_UP, 8));
    }

    @Test
    void headingUpSourceExtentCoversFortyFiveDegreeCorners() {
        assertEquals(120.0 * Math.sqrt(2.0),
                TileCoverage.sourceHalfExtentBlocks(240, 1.0, MapOrientation.HEADING_UP), EPSILON);
        assertEquals(120.0,
                TileCoverage.sourceHalfExtentBlocks(240, 1.0, MapOrientation.NORTH_UP), EPSILON);
    }

    @ParameterizedTest
    @CsvSource({
            "40,NORTH_UP,20.0", "40,HEADING_UP,28.284271247461902",
            "100,NORTH_UP,50.0", "100,HEADING_UP,70.71067811865476"
    })
    void smallAndLargestViewportsKeepOrientationCoverage(int size, MapOrientation orientation,
                                                         double expectedExtent) {
        assertEquals(expectedExtent,
                TileCoverage.sourceHalfExtentBlocks(size, 1.0, orientation), EPSILON);
    }

    @ParameterizedTest
    @ValueSource(ints = {40, 50, 60, 70, 80, 90, 100})
    void compassAndHeadingUpCoverageRemainFiniteForEverySelectableSize(int size) {
        MapTransform.Point north = MapTransform.northIndicatorOffset(
                size, 45, MapOrientation.HEADING_UP, 8);
        assertTrue(Double.isFinite(north.x()));
        assertTrue(Double.isFinite(north.y()));
        assertTrue(Math.abs(north.x()) <= size / 2.0);
        assertTrue(Math.abs(north.y()) <= size / 2.0);

        Set<TileAddress> tiles = TileCoverage.requiredTiles("overworld", 250, 250,
                500, 500, size, 4.0, MapOrientation.HEADING_UP);
        assertFalse(tiles.isEmpty());
        assertTrue(tiles.contains(TileAddress.atWorldPosition("overworld", 250, 250, 500, 500)));
    }

    @Test
    void commonHeadingUpViewportKeepsThreeByThreePrefetch() {
        Set<TileAddress> tiles = TileCoverage.requiredTiles("overworld", 250, 250,
                500, 500, 80, 2.0, MapOrientation.HEADING_UP);
        assertEquals(9, tiles.size());
        assertTrue(tiles.contains(new TileAddress("overworld", -1, -1, 1)));
        assertTrue(tiles.contains(new TileAddress("overworld", 1, 1, 1)));
    }

    @Test
    void maximumViewportAddsOnlyTilesNeededAtBoundary() {
        Set<TileAddress> tiles = TileCoverage.requiredTiles("overworld", 499, 499,
                500, 500, 240, 4.0, MapOrientation.HEADING_UP);
        assertEquals(16, tiles.size());
        assertTrue(tiles.contains(new TileAddress("overworld", -1, -1, 1)));
        assertTrue(tiles.contains(new TileAddress("overworld", 2, 2, 1)));
    }

    @ParameterizedTest
    @CsvSource({
            "250,250", "499.9,250", "500,500", "-0.1,-0.1", "-500,-500"
    })
    void rotatedViewportAlwaysContainsCenterAndPrefetchNeighbors(double x, double z) {
        Set<TileAddress> tiles = TileCoverage.requiredTiles("overworld", x, z,
                500, 500, 144, 2.0, MapOrientation.HEADING_UP);
        TileAddress center = TileAddress.atWorldPosition("overworld", x, z, 500, 500);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertTrue(tiles.contains(new TileAddress("overworld", center.x() + dx, center.z() + dz, 1)));
            }
        }
    }

    @Test
    void continuousYawChangesDoNotChangeWantedTileSet() {
        Set<TileAddress> baseline = TileCoverage.requiredTiles("overworld", -0.25, 500.25,
                500, 500, 240, 4.0, MapOrientation.HEADING_UP);
        for (int yaw = -720; yaw <= 720; yaw++) {
            assertEquals(baseline, TileCoverage.requiredTiles("overworld", -0.25, 500.25,
                    500, 500, 240, 4.0, MapOrientation.HEADING_UP));
            assertTrue(Double.isFinite(MapTransform.mapRotationDegrees(yaw, MapOrientation.HEADING_UP)));
        }
    }

    private static void assertPoint(double x, double y, MapTransform.Point actual) {
        assertEquals(x, actual.x(), EPSILON);
        assertEquals(y, actual.y(), EPSILON);
    }
}
