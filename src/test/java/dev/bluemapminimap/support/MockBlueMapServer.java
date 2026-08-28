package dev.bluemapminimap.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MockBlueMapServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final BlockingQueue<String> sseEvents = new LinkedBlockingQueue<>();
    private final AtomicInteger tileRequests = new AtomicInteger();
    private final AtomicInteger sseConnections = new AtomicInteger();
    private volatile CountDownLatch sseDisconnected = new CountDownLatch(1);
    private volatile int sseStatus = 200;
    private volatile long slowDelayMillis = 500;

    public MockBlueMapServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public URI root() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    public int tileRequests() {
        return tileRequests.get();
    }

    public int sseConnections() {
        return sseConnections.get();
    }

    public void setSseStatus(int status) {
        this.sseStatus = status;
    }

    public void setSlowDelayMillis(long delay) {
        this.slowDelayMillis = delay;
    }

    public void sendSse(String event, String data) throws InterruptedException {
        if (!sseEvents.offer("event: " + event + "\ndata: " + data + "\n\n", 2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("SSE event queue is full");
        }
    }

    public boolean awaitDisconnect() throws InterruptedException {
        return sseDisconnected.await(2, TimeUnit.SECONDS);
    }

    public void resetDisconnectLatch() {
        sseDisconnected = new CountDownLatch(1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/settings.json")) {
            json(exchange, 200, "{\"mapDataRoot\":\"maps\",\"liveDataRoot\":\"maps\",\"maps\":[\"overworld\"]}");
            return;
        }
        if (path.equals("/maps/overworld/settings.json")) {
            json(exchange, 200, "{\"lowres\":{\"tileSize\":[2,2],\"lodFactor\":2,\"lodCount\":3}}");
            return;
        }
        if (path.equals("/maps/overworld/live/players.json")) {
            json(exchange, 200, playersJson());
            return;
        }
        if (path.equals("/maps/overworld/live/sse")) {
            handleSse(exchange);
            return;
        }
        if (path.startsWith("/maps/overworld/tiles/1/")) {
            tileRequests.incrementAndGet();
            if ("tile-v1".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.getResponseHeaders().set("ETag", "tile-v1");
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }
            byte[] png = lowresPng();
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("ETag", "tile-v1");
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
            return;
        }
        if (path.equals("/slow")) {
            try {
                Thread.sleep(slowDelayMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            json(exchange, 200, "{}");
            return;
        }
        if (path.equals("/oversized")) {
            byte[] body = new byte[4096];
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }

    private void handleSse(HttpExchange exchange) throws IOException {
        if (sseStatus != 200) {
            exchange.sendResponseHeaders(sseStatus, -1);
            exchange.close();
            return;
        }
        sseConnections.incrementAndGet();
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        try {
            while (true) {
                String event = sseEvents.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) event = ": keepalive\n\n";
                exchange.getResponseBody().write(event.getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // Client-side close is the expected disconnect signal.
        } finally {
            sseDisconnected.countDown();
            exchange.close();
        }
    }

    private static void json(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String playersJson() {
        return "{\"players\":[{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Alex\","
                + "\"foreign\":false,\"position\":{\"x\":1,\"y\":64,\"z\":2},\"rotation\":{\"yaw\":0}}]}";
    }

    private static byte[] lowresPng() throws IOException {
        BufferedImage image = new BufferedImage(3, 6, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) image.setRGB(x, y, Color.GREEN.getRGB());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
