package dev.bluemapminimap.net;

import dev.bluemapminimap.model.FetchResult;
import dev.bluemapminimap.model.ResourceValidators;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

public final class SafeHttpClient implements AutoCloseable {
    public static final String USER_AGENT = "BlueMapMinimap/0.1";

    private final HttpClient client;
    private final Duration requestTimeout;

    public SafeHttpClient() {
        this(Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    public SafeHttpClient(Duration connectTimeout, Duration requestTimeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.requestTimeout = requestTimeout;
    }

    public FetchResult get(URI uri, int maxBytes, ResourceValidators validators, String accept) throws HttpFailure, InterruptedException {
        return get(uri, maxBytes, validators == null ? ResourceValidators.NONE : validators, accept, 0);
    }

    public SseStream openSse(URI uri) throws HttpFailure, InterruptedException {
        HttpResponse<InputStream> response = send(buildRequest(uri, ResourceValidators.NONE, "text/event-stream"));
        int status = response.statusCode();
        if (isRedirect(status)) {
            closeQuietly(response.body());
            URI redirected = checkedRedirect(uri, response, 0);
            response = send(buildRequest(redirected, ResourceValidators.NONE, "text/event-stream"));
            status = response.statusCode();
        }
        if (status != 200) {
            closeQuietly(response.body());
            throw new HttpFailure(HttpFailure.Category.HTTP_STATUS, "SSE request returned HTTP " + status, status, null);
        }
        String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if (!type.startsWith("text/event-stream")) {
            closeQuietly(response.body());
            throw new HttpFailure(HttpFailure.Category.UNSUPPORTED_CONTENT, "SSE response has an unexpected content type");
        }
        return new SseStream(response.body());
    }

    private FetchResult get(URI uri, int maxBytes, ResourceValidators validators, String accept, int redirects)
            throws HttpFailure, InterruptedException {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        HttpResponse<InputStream> response = send(buildRequest(uri, validators, accept));
        int status = response.statusCode();
        if (isRedirect(status)) {
            closeQuietly(response.body());
            URI redirected = checkedRedirect(uri, response, redirects);
            return get(redirected, maxBytes, validators, accept, redirects + 1);
        }
        ResourceValidators received = new ResourceValidators(
                response.headers().firstValue("ETag").orElse(null),
                response.headers().firstValue("Last-Modified").orElse(null)
        );
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (status == 304) {
            closeQuietly(response.body());
            return new FetchResult(FetchResult.Status.NOT_MODIFIED, new byte[0], received, contentType);
        }
        if (status == 204 || status == 404) {
            closeQuietly(response.body());
            return FetchResult.missing();
        }
        if (status < 200 || status >= 300) {
            closeQuietly(response.body());
            throw new HttpFailure(HttpFailure.Category.HTTP_STATUS, "HTTP request returned status " + status, status, null);
        }
        Optional<String> lengthHeader = response.headers().firstValue("Content-Length");
        if (lengthHeader.isPresent()) {
            try {
                if (Long.parseLong(lengthHeader.get()) > maxBytes) {
                    closeQuietly(response.body());
                    throw new HttpFailure(HttpFailure.Category.RESPONSE_TOO_LARGE, "HTTP response exceeds the configured size limit");
                }
            } catch (NumberFormatException ignored) {
                // The bounded stream read remains authoritative.
            }
        }
        try (InputStream input = response.body()) {
            return new FetchResult(FetchResult.Status.OK, readBounded(input, maxBytes), received, contentType);
        } catch (HttpFailure ex) {
            throw ex;
        } catch (IOException ex) {
            throw new HttpFailure(HttpFailure.Category.IO, "Failed while reading an HTTP response", status, ex);
        }
    }

    private HttpResponse<InputStream> send(HttpRequest request) throws HttpFailure, InterruptedException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException ex) {
            throw new HttpFailure(HttpFailure.Category.TIMEOUT, "HTTP request timed out", -1, ex);
        } catch (IOException ex) {
            throw new HttpFailure(HttpFailure.Category.IO, "HTTP connection failed", -1, ex);
        }
    }

    private HttpRequest buildRequest(URI uri, ResourceValidators validators, String accept) throws HttpFailure {
        validateUri(uri);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Encoding", "identity")
                .GET();
        if (validators.etag() != null && !validators.etag().isBlank()) builder.header("If-None-Match", validators.etag());
        if (validators.lastModified() != null && !validators.lastModified().isBlank()) {
            builder.header("If-Modified-Since", validators.lastModified());
        }
        return builder.build();
    }

    private static void validateUri(URI uri) throws HttpFailure {
        String scheme = uri == null ? null : uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) || uri.getHost() == null) {
            throw new HttpFailure(HttpFailure.Category.INVALID_URI, "HTTP URI is invalid");
        }
        if (uri.getUserInfo() != null) throw new HttpFailure(HttpFailure.Category.INVALID_URI, "HTTP URI contains user information");
    }

    private static URI checkedRedirect(URI source, HttpResponse<?> response, int redirects) throws HttpFailure {
        if (redirects >= 2) throw new HttpFailure(HttpFailure.Category.TOO_MANY_REDIRECTS, "HTTP request exceeded the redirect limit");
        String rawLocation = response.headers().firstValue("Location")
                .orElseThrow(() -> new HttpFailure(HttpFailure.Category.HTTP_STATUS, "HTTP redirect did not contain a Location header"));
        URI destination;
        try {
            destination = source.resolve(rawLocation);
        } catch (IllegalArgumentException ex) {
            throw new HttpFailure(HttpFailure.Category.INVALID_URI, "HTTP redirect target is invalid", -1, ex);
        }
        validateUri(destination);
        if (!sameHost(source, destination)) {
            throw new HttpFailure(HttpFailure.Category.CROSS_HOST_REDIRECT, "HTTP redirect changed the destination host");
        }
        if (source.getScheme().equalsIgnoreCase("https") && destination.getScheme().equalsIgnoreCase("http")) {
            throw new HttpFailure(HttpFailure.Category.INVALID_URI, "HTTP redirect attempted to downgrade HTTPS");
        }
        return destination;
    }

    private static boolean sameHost(URI left, URI right) {
        return left.getHost().equalsIgnoreCase(right.getHost()) && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException, HttpFailure {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) throw new HttpFailure(HttpFailure.Category.RESPONSE_TOO_LARGE, "HTTP response exceeds the configured size limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
