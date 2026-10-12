package dev.bluemapminimap.config;

import dev.bluemapminimap.protocol.BlueMapUris;
import java.net.URI;

/** Validates a single in-game form and merges it without replacing unrelated profiles. */
public final class ServerProfileEditor {
    private ServerProfileEditor() { }

    public static void apply(MinimapConfig config, String address, String url, String dimension, String map) {
        String key = MinimapConfig.normalizeServerAddress(address);
        URI server = URI.create("minecraft://" + key);
        if (key.isBlank() || server.getHost() == null || server.getUserInfo() != null
                || !server.getPath().isEmpty() || server.getQuery() != null || server.getFragment() != null
                || server.getPort() == 0 || server.getPort() > 65535) {
            throw new IllegalArgumentException("Invalid Minecraft address");
        }
        String base = url.isBlank() ? "" : BlueMapUris.validatedBase(url).toString();
        String world = dimension.strip();
        if (!world.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid dimension key");
        String selected = map == null ? "" : map.strip();
        if (!selected.isEmpty()) BlueMapUris.mapRoot(URI.create("https://example.invalid/"), "maps", selected);
        ServerProfile existing = config.profileFor(key);
        ServerProfile updated = existing == null ? new ServerProfile() : existing.copy();
        updated.blueMapUrl = base;
        if (selected.isEmpty()) updated.dimensions.remove(world);
        else updated.dimensions.put(world, selected);
        // Update the original key too, avoiding two entries for case/default-port aliases.
        String storedKey = config.servers.keySet().stream()
                .filter(candidate -> MinimapConfig.normalizeServerAddress(candidate).equals(key))
                .findFirst().orElse(key);
        config.servers.put(storedKey, updated);
    }
}
