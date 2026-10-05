package dev.bluemapminimap.protocol;

import dev.bluemapminimap.net.SafeHttpClient;
import dev.bluemapminimap.support.MockBlueMapServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueMapDiscoveryTest {
    @Test
    void candidatesStayHttpsOnlyAndWithinTheMinecraftParentDomain() {
        assertEquals(List.of(
                URI.create("https://mc.example.com/"),
                URI.create("https://mc.example.com/bluemap/"),
                URI.create("https://mcmap.example.com/"),
                URI.create("https://map.example.com/"),
                URI.create("https://bluemap.example.com/")
        ), BlueMapDiscovery.candidates("MC.Example.com:25565"));
    }

    @Test
    void invalidAndIpv6AddressesDoNotCauseUnboundedDiscovery() {
        assertTrue(BlueMapDiscovery.candidates("[2001:db8::1]:25565").isEmpty());
        assertTrue(BlueMapDiscovery.candidates("bad.example/path").isEmpty());
        assertTrue(BlueMapDiscovery.candidates("user@example.com").isEmpty());
        assertEquals(2, BlueMapDiscovery.candidates("192.0.2.10:25565").size());
    }

    @Test
    void directIpv4UsesReverseDnsBeforeExactHostCandidates() {
        List<URI> candidates = BlueMapDiscovery.candidatesWithReverseDns(
                "192.0.2.20:25565", ignored -> Optional.of("mc.example.com."));

        assertEquals(List.of(
                URI.create("https://mc.example.com/"),
                URI.create("https://mc.example.com/bluemap/"),
                URI.create("https://mcmap.example.com/"),
                URI.create("https://map.example.com/"),
                URI.create("https://bluemap.example.com/"),
                URI.create("https://192.0.2.20/"),
                URI.create("https://192.0.2.20/bluemap/")
        ), candidates);
    }

    @Test
    void failedReverseDnsFallsBackToExactIpv4Only() {
        List<URI> candidates = BlueMapDiscovery.candidatesWithReverseDns(
                "192.0.2.20", ignored -> Optional.empty());

        assertEquals(List.of(
                URI.create("https://192.0.2.20/"),
                URI.create("https://192.0.2.20/bluemap/")
        ), candidates);
    }

    @Test
    void privateIpv4TriesOnlyItsOwnStandardBlueMapPortAsHttpFallback() {
        List<URI> candidates = BlueMapDiscovery.candidatesWithReverseDns(
                "192.168.1.20", ignored -> Optional.of("mc.example.com"));

        assertEquals(URI.create("http://192.168.1.20:8100/"), candidates.get(0));
        assertEquals(URI.create("http://192.168.1.20:8100/bluemap/"), candidates.get(1));
        assertEquals(9, candidates.size());
    }

    @Test
    void validatesSettingsAndLowresBeforeAcceptingCandidate() throws Exception {
        try (MockBlueMapServer server = new MockBlueMapServer();
             SafeHttpClient client = new SafeHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(2))) {
            BlueMapDiscovery.Result result = BlueMapDiscovery.discover(client,
                    List.of(server.root().resolve("missing/"), server.root()),
                    "minecraft:overworld").orElseThrow();
            assertEquals(server.root(), result.base());
            assertEquals("overworld", result.mapId());
        }
    }

    @Test
    void refusesEndpointWithoutUsableMapForCurrentDimension() throws Exception {
        try (MockBlueMapServer server = new MockBlueMapServer();
             SafeHttpClient client = new SafeHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(2))) {
            assertTrue(BlueMapDiscovery.discover(client, List.of(server.root()), "minecraft:the_nether").isEmpty());
        }
    }
}
