package dev.bluemapminimap;

import dev.bluemapminimap.cache.CacheEntry;
import dev.bluemapminimap.cache.DiskTileCache;
import dev.bluemapminimap.concurrent.DownloadQueue;
import dev.bluemapminimap.model.FetchResult;
import dev.bluemapminimap.model.ResourceValidators;
import dev.bluemapminimap.model.TileAddress;
import dev.bluemapminimap.net.HttpFailure;
import dev.bluemapminimap.net.SafeHttpClient;
import dev.bluemapminimap.net.SseStream;
import dev.bluemapminimap.protocol.BlueMapJson;
import dev.bluemapminimap.protocol.BlueMapUris;
import dev.bluemapminimap.protocol.PngHeader;
import dev.bluemapminimap.protocol.SseParser;
import dev.bluemapminimap.support.MockBlueMapServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PocAcceptanceTest {
    @TempDir Path temporary;
    private MockBlueMapServer server;
    private SafeHttpClient client;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockBlueMapServer();
        client = new SafeHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() {
        client.close();
        server.close();
    }

    @Test
    void test01SettingsFetch() throws Exception {
        FetchResult result = client.get(BlueMapUris.rootSettings(server.root()), 1024 * 1024, ResourceValidators.NONE, "application/json");
        assertEquals(List.of("overworld"), BlueMapJson.parseGlobalSettings(result.body()).maps());
    }

    @Test
    void test02MapSettingsFetch() throws Exception {
        FetchResult result = client.get(BlueMapUris.mapSettings(server.root(), "maps", "overworld"), 1024 * 1024,
                ResourceValidators.NONE, "application/json");
        assertArrayEquals(new int[]{2, 2, 2, 3}, BlueMapJson.parseLowresLayout(result.body()));
    }

    @Test
    void test03PositiveCoordinateTileCalculation() {
        assertEquals(new TileAddress("overworld", 2, 1, 1), TileAddress.atWorldPosition("overworld", 5.9, 3.9, 2, 2));
    }

    @Test
    void test04NegativeCoordinateTileCalculation() {
        assertEquals(new TileAddress("overworld", -1, -2, 1), TileAddress.atWorldPosition("overworld", -0.01, -2.01, 2, 2));
    }

    @Test
    void test05TileDownload() throws Exception {
        TileAddress tile = new TileAddress("overworld", 0, 0, 1);
        FetchResult result = tile(tile, ResourceValidators.NONE, client);
        assertEquals(FetchResult.Status.OK, result.status());
        assertEquals(new PngHeader.Dimensions(3, 6), PngHeader.validateLowres(result.body(), 2, 2));
    }

    @Test
    void test06DuplicateSuppression() throws Exception {
        try (DownloadQueue queue = new DownloadQueue(1, 4)) {
            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            assertNotNull(queue.submitOnce("tile", () -> await(running, release)));
            assertTrue(running.await(2, TimeUnit.SECONDS));
            assertNull(queue.submitOnce("tile", () -> fail("duplicate ran")));
            release.countDown();
        }
    }

    @Test
    void test07PersistentCache() throws Exception {
        DiskTileCache cache = new DiskTileCache(temporary.resolve("cache"));
        TileAddress tile = new TileAddress("overworld", 0, 0, 1);
        FetchResult fetched = tile(tile, ResourceValidators.NONE, client);
        cache.write(server.root(), tile, fetched.body(), fetched.validators());
        CacheEntry restored = cache.read(server.root(), tile).orElseThrow();
        assertArrayEquals(fetched.body(), restored.bytes());
    }

    @Test
    void test08ConditionalGetWithEtag() throws Exception {
        TileAddress tile = new TileAddress("overworld", 0, 0, 1);
        FetchResult first = tile(tile, ResourceValidators.NONE, client);
        FetchResult second = tile(tile, first.validators(), client);
        assertEquals(FetchResult.Status.NOT_MODIFIED, second.status());
        assertEquals(2, server.tileRequests());
    }

    @Test
    void test09SseTileEvent() throws Exception {
        try (SseStream stream = client.openSse(server.root().resolve("maps/overworld/live/sse"))) {
            server.sendSse("tile", "{\"x\":1,\"y\":-2,\"lod\":1}");
            List<String> events = readOneEvent(stream);
            assertEquals("tile", events.get(0));
            assertEquals(1, BlueMapJson.parseTileEvent(events.get(1)).x());
        }
    }

    @Test
    void test10SsePlayerEvent() throws Exception {
        String players = "{\"players\":[{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Alex\","
                + "\"foreign\":false,\"position\":{\"x\":1,\"y\":64,\"z\":2},\"rotation\":{\"yaw\":0}}]}";
        try (SseStream stream = client.openSse(server.root().resolve("maps/overworld/live/sse"))) {
            server.sendSse("player", players);
            List<String> event = readOneEvent(stream);
            assertEquals("Alex", BlueMapJson.parsePlayers(event.get(1).getBytes(StandardCharsets.UTF_8)).getFirst().name());
        }
    }

    @Test
    void test11DisconnectClosesSse() throws Exception {
        SseStream stream = client.openSse(server.root().resolve("maps/overworld/live/sse"));
        stream.close();
        server.sendSse("player", "{}");
        assertTrue(server.awaitDisconnect());
    }

    @Test
    void test12SseReconnect() throws Exception {
        SseStream first = client.openSse(server.root().resolve("maps/overworld/live/sse"));
        first.close();
        server.sendSse("tile", "{}");
        assertTrue(server.awaitDisconnect());
        server.resetDisconnectLatch();
        try (SseStream ignored = client.openSse(server.root().resolve("maps/overworld/live/sse"))) {
            assertEquals(2, server.sseConnections());
        }
    }

    @Test
    void test13PollingFallback() throws Exception {
        server.setSseStatus(503);
        assertThrows(HttpFailure.class, () -> client.openSse(server.root().resolve("maps/overworld/live/sse")));
        FetchResult players = client.get(server.root().resolve("maps/overworld/live/players.json"), 1024 * 1024,
                ResourceValidators.NONE, "application/json");
        assertEquals("Alex", BlueMapJson.parsePlayers(players.body()).getFirst().name());
    }

    @Test
    void test14WorldChangeCancellation() throws Exception {
        try (DownloadQueue queue = new DownloadQueue(1, 4)) {
            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch interrupted = new CountDownLatch(1);
            Future<?> request = queue.submitOnce("old-world", () -> {
                running.countDown();
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ex) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            });
            assertNotNull(request);
            assertTrue(running.await(2, TimeUnit.SECONDS));
            assertTrue(request.cancel(true));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void test15InvalidPngRejection() {
        assertThrows(Exception.class, () -> PngHeader.validateLowres("not a png".getBytes(StandardCharsets.UTF_8), 2, 2));
    }

    @Test
    void test16Timeout() throws Exception {
        server.setSlowDelayMillis(500);
        try (SafeHttpClient shortClient = new SafeHttpClient(Duration.ofMillis(100), Duration.ofMillis(100))) {
            HttpFailure failure = assertThrows(HttpFailure.class,
                    () -> shortClient.get(server.root().resolve("slow"), 1024, ResourceValidators.NONE, "application/json"));
            assertEquals(HttpFailure.Category.TIMEOUT, failure.category());
        }
    }

    @Test
    void test17OversizedResponse() {
        HttpFailure failure = assertThrows(HttpFailure.class,
                () -> client.get(server.root().resolve("oversized"), 128, ResourceValidators.NONE, "application/octet-stream"));
        assertEquals(HttpFailure.Category.RESPONSE_TOO_LARGE, failure.category());
    }

    private FetchResult tile(TileAddress tile, ResourceValidators validators, SafeHttpClient using) throws Exception {
        URI mapRoot = server.root().resolve("maps/overworld/");
        return using.get(BlueMapUris.lowresTile(mapRoot, tile), 8 * 1024 * 1024, validators, "image/png");
    }

    private static List<String> readOneEvent(SseStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream.input(), StandardCharsets.UTF_8));
        List<String> result = new ArrayList<>();
        SseParser parser = new SseParser((event, data) -> {
            result.add(event);
            result.add(data);
        });
        String line;
        while (result.isEmpty() && (line = reader.readLine()) != null) parser.acceptLine(line);
        return result;
    }

    private static void await(CountDownLatch running, CountDownLatch release) {
        running.countDown();
        try {
            release.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
