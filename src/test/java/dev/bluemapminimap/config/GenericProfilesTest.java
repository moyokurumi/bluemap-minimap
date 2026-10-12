package dev.bluemapminimap.config;

import dev.bluemapminimap.protocol.BlueMapUris;
import dev.bluemapminimap.protocol.MapSelection;
import dev.bluemapminimap.cache.DiskTileCache;
import dev.bluemapminimap.model.TileAddress;
import dev.bluemapminimap.model.ResourceValidators;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GenericProfilesTest {
    @TempDir Path temporary;

    @Test void newInstallContainsNoServerProfiles() throws Exception {
        var store = new ConfigStore(temporary.resolve("config.json"));
        assertTrue(store.load().servers.isEmpty());
        assertTrue(store.load().autoDiscoverBlueMap);
        assertTrue(store.load().servers.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"servers\":{}}", "{\"servers\":null}"})
    void emptyLegacyFilesAreNotRewrittenOrFilledWithAServer(String original) throws Exception {
        Path file = temporary.resolve("config.json");
        Files.writeString(file, original);
        assertTrue(new ConfigStore(file).load().servers.isEmpty());
        assertEquals(original, Files.readString(file));
    }

    @Test void upgradePreservesSavedProfilesPreferencesAndFirstBackup() throws Exception {
        Path file = temporary.resolve("config.json");
        String original = "{\"size\":92,\"autoDiscoverBlueMap\":false,\"servers\":{\"PLAY.EXAMPLE.COM:25565\":{"
                + "\"blueMapUrl\":\"https://map.example.com/\",\"dimensions\":{\"minecraft:resource\":\"mining\"}}}}";
        Files.writeString(file, original);
        var store = new ConfigStore(file);
        MinimapConfig config = store.load();
        assertEquals(92, config.size);
        assertFalse(config.autoDiscoverBlueMap);
        assertEquals("mining", config.profileFor("play.example.com").dimensions.get("minecraft:resource"));
        assertEquals(original, Files.readString(file));
        config.size = 60;
        store.save(config);
        config.size = 40;
        store.save(config);
        assertEquals(original, Files.readString(file.resolveSibling("config.json.pre-rc4.bak")));
        assertEquals("https://map.example.com/", store.load().profileFor("play.example.com").blueMapUrl);
    }

    @Test void malformedConfigIsPreservedBeforeExplicitSave() throws Exception {
        Path file = temporary.resolve("config.json");
        Files.writeString(file, "{broken");
        var store = new ConfigStore(file);
        assertThrows(java.io.IOException.class, store::load);
        assertEquals("{broken", Files.readString(file));
        store.save(new MinimapConfig());
        assertEquals("{broken", Files.readString(file.resolveSibling("config.json.pre-rc4.bak")));
    }

    @Test void uiEditsMergeAliasesWithoutLosingOtherServersOrWorlds() {
        MinimapConfig config = new MinimapConfig();
        var existing = new ServerProfile();
        existing.blueMapUrl = "https://map.example.com/";
        existing.dimensions.put("minecraft:the_nether", "hell");
        config.servers.put("PLAY.EXAMPLE.COM:25565", existing);
        config.servers.put("other.example.com", new ServerProfile());
        ServerProfileEditor.apply(config, "play.example.com", "https://map.example.com", "minecraft:overworld", "surface");
        assertEquals(2, config.servers.size());
        assertEquals("hell", config.profileFor("play.example.com").dimensions.get("minecraft:the_nether"));
        assertEquals("surface", config.profileFor("play.example.com").dimensions.get("minecraft:overworld"));
        assertEquals("https://map.example.com/", config.profileFor("play.example.com").blueMapUrl);
        ServerProfileEditor.apply(config, "play.example.com", "https://map.example.com/", "minecraft:overworld", "");
        assertFalse(config.profileFor("play.example.com").dimensions.containsKey("minecraft:overworld"));
        assertEquals("hell", config.profileFor("play.example.com").dimensions.get("minecraft:the_nether"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://user:secret@example.com/", "https://example.com/?token=example",
            "https://example.com/#map", "file:///tmp/map", "https://example.com:99999/"})
    void unsafeUrlsCannotBeSavedByTheForm(String url) {
        MinimapConfig config = new MinimapConfig();
        assertThrows(IllegalArgumentException.class,
                () -> ServerProfileEditor.apply(config, "play.example.com", url, "minecraft:overworld", "world"));
        assertTrue(config.servers.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BlueMapUris.validatedBase(url));
    }

    @Test void mapSelectionRejectsStaleUnknownAndAmbiguousMaps() {
        var profile = new ServerProfile();
        assertNull(MapSelection.choose("minecraft:custom", profile, List.of("overworld")));
        assertNull(MapSelection.choose("minecraft:overworld", profile, List.of("arbitrary")));
        assertNull(MapSelection.choose("minecraft:overworld", profile, List.of("overworld", "world")));
        profile.dimensions.put("minecraft:overworld", "missing");
        assertNull(MapSelection.choose("minecraft:overworld", profile, List.of("overworld")));
        assertEquals("world_nether", MapSelection.choose("minecraft:the_nether", profile, List.of("world_nether")));
        assertEquals("the_end", MapSelection.choose("minecraft:the_end", profile, List.of("the_end")));
    }

    @Test void identicalMapIdsOnDifferentEndpointsNeverShareCachedTiles() throws Exception {
        var cache = new DiskTileCache(temporary.resolve("cache"));
        var tile = new TileAddress("world", 0, 0, 1);
        URI first = URI.create("https://map.example.com/server-a/");
        URI second = URI.create("https://map.example.com/server-b/");
        URI otherHost = URI.create("https://another.example.com/server-a/");
        cache.write(first, tile, new byte[]{1, 2, 3}, ResourceValidators.NONE);
        assertTrue(cache.read(second, tile).isEmpty());
        assertTrue(cache.read(otherHost, tile).isEmpty());
        assertArrayEquals(new byte[]{1, 2, 3}, cache.read(first, tile).orElseThrow().bytes());
    }
}
