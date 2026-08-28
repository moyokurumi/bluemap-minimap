package dev.bluemapminimap.client;

import dev.bluemapminimap.client.render.ManagedTexture;
import dev.bluemapminimap.config.MinimapConfig;
import dev.bluemapminimap.model.MapDescriptor;
import dev.bluemapminimap.model.RemotePlayer;
import dev.bluemapminimap.model.TileAddress;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuntimeView(
        MinimapConfig config,
        MapDescriptor map,
        double playerX,
        double playerZ,
        float playerYaw,
        UUID localPlayerUuid,
        Map<TileAddress, ManagedTexture> tiles,
        Map<UUID, ManagedTexture> heads,
        List<RemotePlayer> players
) {
    public static RuntimeView empty(MinimapConfig config) {
        return new RuntimeView(config, null, 0, 0, 0, null, Map.of(), Map.of(), List.of());
    }
}
