package dev.bluemapminimap.protocol;

import dev.bluemapminimap.config.ServerProfile;
import dev.bluemapminimap.model.RemotePlayer;
import dev.bluemapminimap.model.TileAddress;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test
    void floorDivisionHandlesPositiveAndNegativeWorldCoordinates() {
        assertEquals(new TileAddress("overworld", 0, 0, 1), TileAddress.atWorldPosition("overworld", 499.9, 0, 500, 500));
        assertEquals(new TileAddress("overworld", -1, -2, 1), TileAddress.atWorldPosition("overworld", -0.1, -500.1, 500, 500));
    }

    @Test
    void positiveTileUriUsesBlueMapDigitFolders() {
        URI uri = BlueMapUris.lowresTile(URI.create("https://map.example/maps/overworld/"), new TileAddress("overworld", 12, 34, 1));
        assertEquals("https://map.example/maps/overworld/tiles/1/x1/2/z3/4.png", uri.toString());
    }

    @Test
    void negativeTileUriUsesBlueMapDigitFolders() {
        URI uri = BlueMapUris.lowresTile(URI.create("https://map.example/maps/overworld/"), new TileAddress("overworld", -12, -3, 1));
        assertEquals("https://map.example/maps/overworld/tiles/1/x-1/2/z-3.png", uri.toString());
    }

    @Test
    void dataRootsRemainRelativeToTheConfiguredOrigin() {
        URI base = URI.create("https://map.example/bluemap/");
        assertEquals("https://map.example/bluemap/data/maps/example-overworld/",
                BlueMapUris.mapRoot(base, "data/maps", "example-overworld").toString());
        assertEquals("https://map.example/bluemap/live/maps/example-overworld/",
                BlueMapUris.liveRoot(base, "live/maps", "example-overworld").toString());
    }

    @Test
    void unsafeDataRootsAndMapIdsAreRejected() {
        URI base = URI.create("https://map.example/");
        assertThrows(IllegalArgumentException.class,
                () -> BlueMapUris.mapRoot(base, "https://unexpected.example/maps", "overworld"));
        assertThrows(IllegalArgumentException.class,
                () -> BlueMapUris.mapRoot(base, "../maps", "overworld"));
        assertThrows(IllegalArgumentException.class,
                () -> BlueMapUris.mapRoot(base, "maps", "../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> BlueMapUris.mapRoot(base, "maps", "%2e%2e"));
    }

    @Test
    void parsesGlobalSettingsAndPublishedMaps() {
        byte[] json = "{\"mapDataRoot\":\"data/maps\",\"liveDataRoot\":\"live/maps\",\"maps\":[\"overworld\",\"resource\"]}"
                .getBytes(StandardCharsets.UTF_8);
        GlobalSettings settings = BlueMapJson.parseGlobalSettings(json);
        assertEquals("data/maps", settings.mapDataRoot());
        assertEquals("live/maps", settings.liveDataRoot());
        assertEquals(List.of("overworld", "resource"), settings.maps());
    }

    @Test
    void parsesActualLowResolutionTileLayout() {
        int[] layout = BlueMapJson.parseLowresLayout("{\"lowres\":{\"tileSize\":[384,512],\"lodFactor\":3,\"lodCount\":4}}"
                .getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(new int[]{384, 512, 3, 4}, layout);
    }

    @Test
    void parsesPlayersAndForeignWorldState() {
        String json = "{\"players\":[{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Alex\","
                + "\"foreign\":false,\"position\":{\"x\":1.5,\"y\":64,\"z\":-2.5},\"rotation\":{\"yaw\":90}}]}";
        List<RemotePlayer> players = BlueMapJson.parsePlayers(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, players.size());
        assertEquals("Alex", players.getFirst().name());
        assertEquals(-2.5, players.getFirst().z());
        assertFalse(players.getFirst().foreign());
    }

    @Test
    void parsesMultilineSseEvents() {
        List<String> events = new ArrayList<>();
        SseParser parser = new SseParser((event, data) -> events.add(event + "=" + data));
        parser.acceptLine("event: marker");
        parser.acceptLine("data: {\"part\":1,");
        parser.acceptLine("data: \"ok\":true}");
        parser.acceptLine("");
        assertEquals(List.of("marker={\"part\":1,\n\"ok\":true}"), events);
    }

    @Test
    void dimensionMappingPrefersExplicitThenOverworldConvention() {
        ServerProfile profile = new ServerProfile();
        profile.dimensions.put("minecraft:custom", "resource");
        List<String> maps = List.of("overworld", "resource");
        assertEquals("resource", MapSelection.choose("minecraft:custom", profile, maps));
        assertEquals("overworld", MapSelection.choose("minecraft:overworld", profile, maps));
        assertNull(MapSelection.choose("minecraft:the_nether", profile, maps));
    }
}
