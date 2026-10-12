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
    void candidatesPreferHttpsAndOnlyUseDefaultHttpPortOnTheExactHost() {
        assertEquals(List.of(
                URI.create("https://mc.example.com/"),
                URI.create("https://mc.example.com/bluemap/"),
                URI.create("https://mcmap.example.com/"),
                URI.create("https://map.example.com/"),
                URI.create("https://bluemap.example.com/"),
                URI.create("http://mc.example.com:8100/"),
                URI.create("http://mc.example.com:8100/bluemap/")
        ), BlueMapDiscovery.candidates("MC.Example.com:25565"));
    }

    @Test
    void invalidAndIpv6AddressesDoNotCauseUnboundedDiscovery() {
        assertTrue(BlueMapDiscovery.candidates("[2001:db8::1]:25565").isEmpty());
        assertTrue(BlueMapDiscovery.candidates("bad.example/path").isEmpty());
        assertTrue(BlueMapDiscovery.candidates("user@example.com").isEmpty());
        assertTrue(BlueMapDiscovery.candidates("mc.example.com:").isEmpty());
        assertTrue(BlueMapDiscovery.candidates("mc.example.com:70000").isEmpty());
        assertEquals(4, BlueMapDiscovery.candidates("192.0.2.10:25565").size());
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
                URI.create("http://mc.example.com:8100/"),
                URI.create("http://mc.example.com:8100/bluemap/"),
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
                URI.create("https://192.0.2.20/bluemap/"),
                URI.create("http://192.0.2.20:8100/"),
                URI.create("http://192.0.2.20:8100/bluemap/")
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

    @Test void guessingNeverEscapesPublicOrPrivateSuffixBoundaries() {
        assertTrue(BlueMapDiscovery.candidates("example.co.uk").contains(URI.create("https://map.example.co.uk/")));
        assertTrue(BlueMapDiscovery.candidates("example.co.uk").stream().noneMatch(uri -> "map.co.uk".equals(uri.getHost())));
        assertTrue(BlueMapDiscovery.candidates("tenant.github.io").stream().noneMatch(uri -> "map.github.io".equals(uri.getHost())));
    }

    @Test void endpointDiscoveryDoesNotRequireChoosingAWorld() throws Exception {
        try (MockBlueMapServer server = new MockBlueMapServer(); SafeHttpClient client = new SafeHttpClient()) {
            var catalog = BlueMapDiscovery.discoverCatalog(client, List.of(server.root())).orElseThrow();
            assertEquals(server.root(), catalog.base());
            assertEquals(List.of("overworld"), catalog.maps());
        }
    }

    @Test void malformedCandidateDoesNotHideALaterUsableEndpoint() throws Exception {
        var bad = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        bad.createContext("/settings.json", exchange -> {
            byte[] body = "{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        bad.start();
        try (MockBlueMapServer good = new MockBlueMapServer(); SafeHttpClient client = new SafeHttpClient()) {
            var badUrl = URI.create("http://127.0.0.1:" + bad.getAddress().getPort() + "/");
            assertEquals(good.root(), BlueMapDiscovery.discoverCatalog(client, List.of(badUrl, good.root())).orElseThrow().base());
        } finally { bad.stop(0); }
    }
}
