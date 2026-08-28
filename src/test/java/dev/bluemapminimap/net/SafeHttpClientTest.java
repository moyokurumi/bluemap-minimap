package dev.bluemapminimap.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.bluemapminimap.model.FetchResult;
import dev.bluemapminimap.model.ResourceValidators;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SafeHttpClientTest {
    private HttpServer server;
    private SafeHttpClient client;
    private URI root;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        client = new SafeHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.stop(0);
    }

    @Test
    void downloadsBoundedJsonWithExplicitUserAgent() throws Exception {
        server.createContext("/settings.json", exchange -> {
            assertEquals(SafeHttpClient.USER_AGENT, exchange.getRequestHeaders().getFirst("User-Agent"));
            respond(exchange, 200, "application/json", "{\"maps\":[]}".getBytes(StandardCharsets.UTF_8));
        });
        FetchResult result = client.get(root.resolve("settings.json"), 1024, ResourceValidators.NONE, "application/json");
        assertEquals(FetchResult.Status.OK, result.status());
    }

    @Test
    void rejectsOversizedResponses() {
        server.createContext("/large", exchange -> respond(exchange, 200, "application/octet-stream", new byte[2048]));
        HttpFailure failure = assertThrows(HttpFailure.class,
                () -> client.get(root.resolve("large"), 128, ResourceValidators.NONE, "application/octet-stream"));
        assertEquals(HttpFailure.Category.RESPONSE_TOO_LARGE, failure.category());
    }

    @Test
    void sendsValidatorsAndAcceptsNotModified() throws Exception {
        server.createContext("/tile", exchange -> {
            assertEquals("tag-1", exchange.getRequestHeaders().getFirst("If-None-Match"));
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
        });
        FetchResult result = client.get(root.resolve("tile"), 1024, new ResourceValidators("tag-1", null), "image/png");
        assertEquals(FetchResult.Status.NOT_MODIFIED, result.status());
    }

    @Test
    void treatsNoContentAndNotFoundAsMissingTiles() throws Exception {
        server.createContext("/empty", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        assertEquals(FetchResult.Status.MISSING,
                client.get(root.resolve("empty"), 1024, ResourceValidators.NONE, "image/png").status());
        assertEquals(FetchResult.Status.MISSING,
                client.get(root.resolve("missing"), 1024, ResourceValidators.NONE, "image/png").status());
    }

    @Test
    void refusesRedirectsToAnotherHost() {
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://example.invalid/secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        HttpFailure failure = assertThrows(HttpFailure.class,
                () -> client.get(root.resolve("redirect"), 1024, ResourceValidators.NONE, "application/json"));
        assertEquals(HttpFailure.Category.CROSS_HOST_REDIRECT, failure.category());
    }

    @Test
    void opensOnlyEventStreamResponses() throws Exception {
        server.createContext("/sse", exchange -> respond(exchange, 200, "text/event-stream", "event: player\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8)));
        try (SseStream stream = client.openSse(root.resolve("sse"))) {
            assertTrue(stream.input().read() >= 0);
        }
        server.createContext("/html", exchange -> respond(exchange, 200, "text/html", "blocked".getBytes(StandardCharsets.UTF_8)));
        HttpFailure failure = assertThrows(HttpFailure.class, () -> client.openSse(root.resolve("html")));
        assertEquals(HttpFailure.Category.UNSUPPORTED_CONTENT, failure.category());
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
