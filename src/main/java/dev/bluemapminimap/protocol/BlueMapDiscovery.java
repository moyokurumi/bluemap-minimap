package dev.bluemapminimap.protocol;

import dev.bluemapminimap.config.ServerProfile;
import dev.bluemapminimap.model.FetchResult;
import dev.bluemapminimap.model.ResourceValidators;
import dev.bluemapminimap.net.HttpFailure;
import dev.bluemapminimap.net.SafeHttpClient;
import com.google.common.net.InternetDomainName;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Safe, bounded BlueMap discovery for clients that have no server profile yet. */
public final class BlueMapDiscovery {
    private static final int JSON_LIMIT = 1024 * 1024;
    private static final int MAX_CANDIDATES = 9;
    private static final Duration REVERSE_DNS_TIMEOUT = Duration.ofSeconds(2);

    private BlueMapDiscovery() {
    }

    /**
     * Produces bounded candidates for the exact Minecraft host and its domain.
     * The default HTTP BlueMap port is tried only on the exact connected host.
     */
    public static List<URI> candidates(String serverAddress) {
        String host = extractHost(serverAddress);
        if (host == null) return List.of();

        return candidatesForHost(host);
    }

    /**
     * Extends normal discovery for direct IPv4 connections with the address's
     * reverse-DNS name. This lets LAN users discover the same public HTTPS
     * BlueMap endpoint as users who connect with the server's DNS name.
     */
    public static List<URI> candidatesWithReverseDns(String serverAddress) {
        return candidatesWithReverseDns(serverAddress, BlueMapDiscovery::reverseDnsWithTimeout);
    }

    static List<URI> candidatesWithReverseDns(String serverAddress, Function<String, Optional<String>> reverseLookup) {
        String host = extractHost(serverAddress);
        if (host == null) return List.of();

        Set<URI> combined = new LinkedHashSet<>();
        if (isPrivateIpv4(host)) {
            combined.add(http(host, 8100, "/"));
            combined.add(http(host, 8100, "/bluemap/"));
        }
        if (isIpAddress(host)) {
            try {
                reverseLookup.apply(host)
                        .map(BlueMapDiscovery::extractHost)
                        .filter(java.util.Objects::nonNull)
                        .filter(reverseHost -> !isIpAddress(reverseHost))
                        .ifPresent(reverseHost -> combined.addAll(candidatesForHost(reverseHost)));
            } catch (RuntimeException ignored) {
                // Reverse DNS is optional. Exact-host discovery remains available.
            }
        }
        combined.addAll(candidatesForHost(host));
        return combined.stream().limit(MAX_CANDIDATES).toList();
    }

    private static List<URI> candidatesForHost(String host) {

        Set<URI> candidates = new LinkedHashSet<>();
        candidates.add(https(host, "/"));
        candidates.add(https(host, "/bluemap/"));

        if (!isIpAddress(host) && host.contains(".")) {
            String[] labels = host.split("\\.");
            String parent = labels.length > 2
                    ? String.join(".", java.util.Arrays.copyOfRange(labels, 1, labels.length))
                    : host;
            // Do not escape a registrable domain (including hosted private suffixes).
            try {
                InternetDomainName name = InternetDomainName.from(host);
                if (!name.isUnderPublicSuffix()
                        || !InternetDomainName.from(parent).isUnderPublicSuffix()) parent = host;
            } catch (IllegalArgumentException ex) {
                parent = host;
            }
            candidates.add(https("mcmap." + parent, "/"));
            candidates.add(https("map." + parent, "/"));
            candidates.add(https("bluemap." + parent, "/"));
        }

        candidates.add(http(host, 8100, "/"));
        candidates.add(http(host, 8100, "/bluemap/"));

        return candidates.stream().limit(MAX_CANDIDATES).toList();
    }

    private static Optional<String> reverseDnsWithTimeout(String host) {
        try {
            return CompletableFuture.supplyAsync(() -> reverseDns(host))
                    .get(REVERSE_DNS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> reverseDns(String host) {
        byte[] address = parseIpv4(host);
        if (address == null) return Optional.empty();
        try {
            String canonical = InetAddress.getByAddress(address).getCanonicalHostName();
            if (canonical == null || canonical.equalsIgnoreCase(host)) return Optional.empty();
            return Optional.of(canonical);
        } catch (UnknownHostException ignored) {
            return Optional.empty();
        }
    }

    private static byte[] parseIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] address = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            try {
                int value = Integer.parseInt(parts[index]);
                if (value < 0 || value > 255) return null;
                address[index] = (byte) value;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return address;
    }

    /**
     * Accepts a candidate only after both root settings and one usable map's
     * low-resolution settings have passed the existing bounded JSON parsers.
     */
    public static Optional<Result> discover(SafeHttpClient http, List<URI> candidates, String dimension)
            throws InterruptedException {
        int checked = 0;
        for (URI candidate : candidates) {
            if (checked++ >= MAX_CANDIDATES) break;
            try {
                FetchResult globalResponse = http.get(BlueMapUris.rootSettings(candidate), JSON_LIMIT,
                        ResourceValidators.NONE, "application/json");
                if (globalResponse.status() != FetchResult.Status.OK || !isJson(globalResponse.contentType())) continue;
                GlobalSettings global = BlueMapJson.parseGlobalSettings(globalResponse.body());
                if (global.maps().isEmpty()) continue;

                ServerProfile provisional = new ServerProfile();
                String mapId = MapSelection.choose(dimension, provisional, global.maps());
                if (mapId == null) continue;

                FetchResult mapResponse = http.get(
                        BlueMapUris.mapSettings(candidate, global.mapDataRoot(), mapId),
                        JSON_LIMIT, ResourceValidators.NONE, "application/json");
                if (mapResponse.status() != FetchResult.Status.OK || !isJson(mapResponse.contentType())) continue;
                BlueMapJson.parseLowresLayout(mapResponse.body());
                return Optional.of(new Result(BlueMapUris.validatedBase(candidate.toString()), mapId));
            } catch (HttpFailure | RuntimeException ignored) {
                // A failed candidate is expected. Continue through the bounded same-domain list.
            }
        }
        return Optional.empty();
    }

    /** Discovers the endpoint independently of world selection, so unusual map IDs can be configured. */
    public static Optional<Catalog> discoverCatalog(SafeHttpClient http, List<URI> candidates)
            throws InterruptedException {
        for (URI candidate : candidates.stream().limit(MAX_CANDIDATES).toList()) {
            try {
                return Optional.of(readCatalog(http, candidate));
            } catch (HttpFailure | RuntimeException ignored) {
                // Failed probes are normal; never log URLs or server responses.
            }
        }
        return Optional.empty();
    }

    public static Catalog readCatalog(SafeHttpClient http, URI base) throws HttpFailure, InterruptedException {
        base = BlueMapUris.validatedBase(base.toString());
        FetchResult response = http.get(BlueMapUris.rootSettings(base), JSON_LIMIT,
                ResourceValidators.NONE, "application/json");
        if (response.status() != FetchResult.Status.OK || !isJson(response.contentType())) {
            throw new IllegalArgumentException("BlueMap settings unavailable");
        }
        GlobalSettings global = BlueMapJson.parseGlobalSettings(response.body());
        List<String> maps = global.maps().stream().distinct().limit(128).toList();
        for (String map : maps) BlueMapUris.mapRoot(base, global.mapDataRoot(), map);
        // Confirm that the endpoint really serves usable BlueMap low-resolution metadata.
        for (String map : maps.stream().limit(8).toList()) {
            FetchResult settings = http.get(BlueMapUris.mapSettings(base, global.mapDataRoot(), map),
                    JSON_LIMIT, ResourceValidators.NONE, "application/json");
            if (settings.status() != FetchResult.Status.OK || !isJson(settings.contentType())) continue;
            try {
                BlueMapJson.parseLowresLayout(settings.body());
                return new Catalog(base, maps);
            } catch (RuntimeException ignored) { }
        }
        throw new IllegalArgumentException("No supported BlueMap maps found");
    }

    public record Catalog(URI base, List<String> maps) {
        public Catalog { maps = List.copyOf(maps); }
    }

    private static String extractHost(String serverAddress) {
        if (serverAddress == null) return null;
        String value = serverAddress.strip();
        if (value.isEmpty() || value.contains("/") || value.contains("@") || value.startsWith("[")) return null;
        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');
        if (firstColon >= 0) {
            if (firstColon != lastColon) return null;
            String port = value.substring(lastColon + 1);
            if (port.isEmpty() || !port.chars().allMatch(Character::isDigit)) return null;
            try {
                int number = Integer.parseInt(port);
                if (number < 1 || number > 65535) return null;
            } catch (NumberFormatException ex) { return null; }
            value = value.substring(0, lastColon);
        }
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        if (value.isBlank()) return null;
        try {
            String ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            return ascii.isBlank() ? null : ascii;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static URI https(String host, String path) {
        return URI.create("https://" + host + path);
    }

    private static URI http(String host, int port, String path) {
        return URI.create("http://" + host + ":" + port + path);
    }

    private static boolean isIpAddress(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPrivateIpv4(String host) {
        byte[] address = parseIpv4(host);
        if (address == null) return false;
        int first = Byte.toUnsignedInt(address[0]);
        int second = Byte.toUnsignedInt(address[1]);
        return first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) return false;
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("application/json") || normalized.startsWith("text/json");
    }

    public record Result(URI base, String mapId) {
    }
}
