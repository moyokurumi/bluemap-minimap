package dev.bluemapminimap.protocol;

import dev.bluemapminimap.model.TileAddress;

import java.net.URI;

public final class BlueMapUris {
    private BlueMapUris() {
    }

    public static URI rootSettings(URI base) {
        return directory(base).resolve("settings.json");
    }

    public static URI mapSettings(URI base, String mapDataRoot, String mapId) {
        return mapRoot(base, mapDataRoot, mapId).resolve("settings.json");
    }

    public static URI players(URI base, String liveDataRoot, String mapId) {
        return liveRoot(base, liveDataRoot, mapId).resolve("live/players.json");
    }

    public static URI markers(URI base, String liveDataRoot, String mapId) {
        return liveRoot(base, liveDataRoot, mapId).resolve("live/markers.json");
    }

    public static URI sse(URI base, String liveDataRoot, String mapId) {
        return liveRoot(base, liveDataRoot, mapId).resolve("live/sse");
    }

    public static URI mapRoot(URI base, String mapDataRoot, String mapId) {
        return dataRoot(base, mapDataRoot, mapId);
    }

    public static URI liveRoot(URI base, String liveDataRoot, String mapId) {
        return dataRoot(base, liveDataRoot, mapId);
    }

    public static URI playerHead(URI mapRoot, String uuid) {
        return directory(mapRoot).resolve("assets/playerheads/" + segment(uuid) + ".png");
    }

    public static URI lowresTile(URI mapRoot, TileAddress tile) {
        String coordinatePath = coordinatePath('x', tile.x()) + coordinatePath('z', tile.z());
        coordinatePath = coordinatePath.substring(0, coordinatePath.length() - 1);
        return directory(mapRoot).resolve("tiles/" + tile.lod() + "/" + coordinatePath + ".png");
    }

    public static URI validatedBase(String raw) {
        URI uri = directory(URI.create(raw.strip()));
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException("BlueMap URL must use HTTPS or HTTP");
        }
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("BlueMap URL must not contain user information");
        if (uri.getHost() == null) throw new IllegalArgumentException("BlueMap URL must contain a host");
        return uri;
    }

    private static URI directory(URI uri) {
        String text = uri.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private static URI dataRoot(URI base, String root, String mapId) {
        return directory(base).resolve(safeRelativePath(root, "maps") + "/" + segment(mapId) + "/");
    }

    private static String safeRelativePath(String value, String fallback) {
        String cleaned = value == null || value.isBlank() ? fallback : value.strip();
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("/")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        if (cleaned.isBlank()) cleaned = fallback;
        String[] parts = cleaned.split("/", -1);
        StringBuilder safe = new StringBuilder();
        for (String part : parts) {
            if (!safe.isEmpty()) safe.append('/');
            safe.append(segment(part));
        }
        return safe.toString();
    }

    private static String coordinatePath(char axis, int coordinate) {
        String digits = Integer.toString(coordinate);
        StringBuilder path = new StringBuilder().append(axis);
        int start = 0;
        if (digits.charAt(0) == '-') {
            path.append('-');
            start = 1;
        }
        for (int index = start; index < digits.length(); index++) {
            path.append(digits.charAt(index)).append('/');
        }
        return path.toString();
    }

    private static String segment(String value) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")
                || cleaned.contains("/") || cleaned.contains("\\") || cleaned.contains(":")
                || cleaned.contains("?") || cleaned.contains("#") || cleaned.contains("%")) {
            throw new IllegalArgumentException("Unsafe BlueMap path segment");
        }
        return cleaned;
    }
}
