package dev.bluemapminimap.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoyoDefaultsTest {
    @TempDir Path temporary;

    @Test
    void cleanInstallPersistsUsableMoyoProfileWithoutEditingJson() throws Exception {
        Path file = temporary.resolve("bluemap-minimap.json");
        MinimapConfig first = new ConfigStore(file).load();
        assertTrue(first.enabled);
        assertEquals(80, first.size);
        assertTrue(Files.isRegularFile(file));
        MinimapConfig restarted = new ConfigStore(file).load();
        ServerProfile profile = MoyoServerPolicy.profileFor(restarted, "MC.MOYOKURUMI.COM:25565");
        assertEquals("https://mcmap.moyokurumi.com/", profile.blueMapUrl);
        assertEquals("overworld", MoyoServerPolicy.mapFor("minecraft:overworld", profile, List.of("overworld", "resource")));
        assertEquals("resource", MoyoServerPolicy.mapFor("minecraft:resource", profile, List.of("overworld", "resource")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mc.moyokurumi.com", "MC.MOYOKURUMI.COM:25565", " mc.moyokurumi.com "})
    void acceptsOnlyTheCanonicalServerAndDefaultPort(String address) {
        assertNotNull(MoyoServerPolicy.profileFor(new MinimapConfig(), address));
    }

    @ParameterizedTest
    @ValueSource(strings = {"other.example", "mc.moyokurumi.com.attacker.example", "mc.moyokurumi.com:25566", "mcmap.moyokurumi.com", "", "localhost"})
    void otherServersNeverReceiveTheMoyoProfileEvenWhenConfigured(String address) {
        MinimapConfig config = new MinimapConfig();
        config.servers.put(address, MoyoServerPolicy.defaults());
        assertNull(MoyoServerPolicy.profileFor(config, address));
        assertFalse(MoyoServerPolicy.supports(address));
    }

    @Test
    void singlePlayerHasNoProfile() {
        assertNull(MoyoServerPolicy.profileFor(new MinimapConfig(), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"servers\":{}}", "{\"servers\":null}", "{\"servers\":{\"mc.moyokurumi.com\":{\"blueMapUrl\":\"\",\"dimensions\":{}}}}"})
    void legacyEmptyProfilesUseDefaultsWithoutChangingOriginalBytes(String original) throws Exception {
        Path file = temporary.resolve("bluemap-minimap.json");
        Files.writeString(file, original);
        MinimapConfig config = new ConfigStore(file).load();
        assertEquals(MoyoServerPolicy.BLUE_MAP, MoyoServerPolicy.profileFor(config, MoyoServerPolicy.SERVER).blueMapUrl);
        assertEquals(original, Files.readString(file));
    }

    @Test
    void legacyOverworldOnlyProfileGetsResourceMappingInMemory() throws Exception {
        Path file = temporary.resolve("bluemap-minimap.json");
        String original = "{\"size\":112,\"autoDiscoverBlueMap\":false,\"servers\":{\"MC.MOYOKURUMI.COM:25565\":{"
                + "\"blueMapUrl\":\"https://mcmap.moyokurumi.com/\",\"dimensions\":{\"minecraft:overworld\":\"overworld\"}}}}";
        Files.writeString(file, original);
        MinimapConfig config = new ConfigStore(file).load();
        assertEquals(112, config.size);
        assertFalse(config.autoDiscoverBlueMap);
        assertEquals("resource", MoyoServerPolicy.profileFor(config, MoyoServerPolicy.SERVER).dimensions.get("minecraft:resource"));
        assertFalse(config.profileFor(MoyoServerPolicy.SERVER).dimensions.containsKey("minecraft:resource"));
        assertEquals(original, Files.readString(file));
    }

    @Test
    void customEndpointAndMappingsArePreserved() {
        MinimapConfig config = new MinimapConfig();
        ServerProfile custom = new ServerProfile();
        custom.blueMapUrl = "https://custom.example/map/";
        custom.dimensions.put("minecraft:overworld", "custom-world");
        config.servers.put(MoyoServerPolicy.SERVER, custom);
        ServerProfile effective = MoyoServerPolicy.profileFor(config, MoyoServerPolicy.SERVER);
        assertEquals(custom.blueMapUrl, effective.blueMapUrl);
        assertEquals(custom.dimensions, effective.dimensions);
        effective.dimensions.clear();
        assertEquals("custom-world", custom.dimensions.get("minecraft:overworld"));
    }

    @Test
    void explicitCustomMapIsNeverReplacedOrGuessedWhenItIsUnpublished() {
        MinimapConfig config = new MinimapConfig();
        config.profileFor(MoyoServerPolicy.SERVER).dimensions.put("minecraft:overworld", "my-private-choice");
        ServerProfile effective = MoyoServerPolicy.profileFor(config, MoyoServerPolicy.SERVER);
        assertEquals("my-private-choice", effective.dimensions.get("minecraft:overworld"));
        assertNull(MoyoServerPolicy.mapFor("minecraft:overworld", effective, List.of("overworld")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:the_nether", "minecraft:the_end", "minecraft:tutorial", "minecraft:unknown"})
    void unsupportedWorldNeverFallsBackToTheOnlyPublishedMap(String dimension) {
        assertNull(MoyoServerPolicy.mapFor(dimension, MoyoServerPolicy.defaults(), List.of("overworld")));
    }

    @Test
    void resourceMapDoesNotFallBackToOverworldWhenResourceIsUnavailable() {
        assertNull(MoyoServerPolicy.mapFor("minecraft:resource", MoyoServerPolicy.defaults(), List.of("overworld")));
    }

    @Test
    void savingSettingsPreservesTheOriginalConfigurationOnce() throws Exception {
        Path file = temporary.resolve("bluemap-minimap.json");
        String original = "{\"size\":92,\"servers\":{\"other.example\":{\"blueMapUrl\":\"https://custom.example/\",\"dimensions\":{}}}}";
        Files.writeString(file, original);
        ConfigStore store = new ConfigStore(file);
        MinimapConfig config = store.load();
        config.size = 60;
        store.save(config);
        config.size = 40;
        store.save(config);
        assertEquals(original, Files.readString(file.resolveSibling("bluemap-minimap.json.pre-rc4.bak")));
        assertEquals("https://custom.example/", store.load().profileFor("other.example").blueMapUrl);
    }

    @Test
    void malformedConfigIsNotOverwrittenOnLoadAndIsBackedUpBeforeExplicitSave() throws Exception {
        Path file = temporary.resolve("bluemap-minimap.json");
        Files.writeString(file, "{broken");
        ConfigStore store = new ConfigStore(file);
        assertThrows(java.io.IOException.class, store::load);
        assertEquals("{broken", Files.readString(file));
        store.save(new MinimapConfig());
        assertEquals("{broken", Files.readString(file.resolveSibling("bluemap-minimap.json.pre-rc4.bak")));
    }
}
