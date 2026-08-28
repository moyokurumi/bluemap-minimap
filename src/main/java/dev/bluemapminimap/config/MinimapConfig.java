package dev.bluemapminimap.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MinimapConfig {
    public boolean enabled = true;
    public int size = 80;
    public HudPosition position = HudPosition.TOP_RIGHT;
    public double zoom = 2.0;
    public boolean showPlayers = true;
    public boolean showNames = true;
    public Map<String, ServerProfile> servers = new LinkedHashMap<>();

    public void sanitize() {
        size = Math.max(80, Math.min(256, size));
        zoom = Math.max(0.5, Math.min(8.0, zoom));
        if (position == null) position = HudPosition.TOP_RIGHT;
        if (servers == null) servers = new LinkedHashMap<>();
        servers.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        servers.replaceAll((key, value) -> {
            if (value.blueMapUrl == null) value.blueMapUrl = "";
            if (value.dimensions == null) value.dimensions = new LinkedHashMap<>();
            return value;
        });
    }

    public ServerProfile profileFor(String serverAddress) {
        if (serverAddress == null) return null;
        String normalized = normalizeServerAddress(serverAddress);
        ServerProfile exact = servers.get(normalized);
        if (exact != null) return exact;
        for (Map.Entry<String, ServerProfile> entry : servers.entrySet()) {
            if (normalizeServerAddress(entry.getKey()).equals(normalized)) return entry.getValue();
        }
        return null;
    }

    public MinimapConfig copy() {
        MinimapConfig copy = new MinimapConfig();
        copy.enabled = enabled;
        copy.size = size;
        copy.position = position;
        copy.zoom = zoom;
        copy.showPlayers = showPlayers;
        copy.showNames = showNames;
        copy.servers.clear();
        servers.forEach((key, value) -> copy.servers.put(key, value.copy()));
        return copy;
    }

    public static String normalizeServerAddress(String address) {
        String normalized = address.strip().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(":25565")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }
        return normalized;
    }
}
