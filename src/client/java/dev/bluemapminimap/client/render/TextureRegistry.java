package dev.bluemapminimap.client.render;

import dev.bluemapminimap.model.TileAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TextureRegistry {
    private static final int MAX_TILES = 64;
    private static final int MAX_HEADS = 64;

    private final Minecraft minecraft;
    private final LinkedHashMap<TileAddress, ManagedTexture> tiles = new LinkedHashMap<>(16, 0.75F, true);
    private final LinkedHashMap<UUID, ManagedTexture> heads = new LinkedHashMap<>(16, 0.75F, true);

    public TextureRegistry(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public synchronized void installTile(TileAddress key, NativeImage image) {
        Identifier id = Identifier.fromNamespaceAndPath("bluemap_minimap", "tile/" + safeTileName(key));
        install(tiles, key, id, image, MAX_TILES, "BlueMap minimap tile");
    }

    public synchronized void installHead(UUID key, NativeImage image) {
        Identifier id = Identifier.fromNamespaceAndPath("bluemap_minimap", "head/" + key.toString().replace("-", ""));
        install(heads, key, id, image, MAX_HEADS, "BlueMap player head");
    }

    public synchronized Map<TileAddress, ManagedTexture> tileSnapshot() {
        return Map.copyOf(tiles);
    }

    public synchronized Map<UUID, ManagedTexture> headSnapshot() {
        return Map.copyOf(heads);
    }

    public synchronized boolean containsTile(TileAddress key) {
        return tiles.containsKey(key);
    }

    public synchronized int tileCount() {
        return tiles.size();
    }

    public synchronized void clear() {
        tiles.values().forEach(texture -> texture.release(minecraft));
        heads.values().forEach(texture -> texture.release(minecraft));
        tiles.clear();
        heads.clear();
    }

    private <K> void install(LinkedHashMap<K, ManagedTexture> target, K key, Identifier id, NativeImage image,
                             int capacity, String label) {
        DynamicTexture dynamic = new DynamicTexture(() -> label, image);
        minecraft.getTextureManager().register(id, dynamic);
        ManagedTexture previous = target.put(key, new ManagedTexture(id, dynamic, image.getWidth(), image.getHeight()));
        if (previous != null && !previous.id().equals(id)) previous.release(minecraft);
        while (target.size() > capacity) {
            Map.Entry<K, ManagedTexture> eldest = target.entrySet().iterator().next();
            target.remove(eldest.getKey());
            eldest.getValue().release(minecraft);
        }
    }

    private static String safeTileName(TileAddress tile) {
        return Integer.toHexString(tile.mapId().hashCode()) + "_" + tile.lod() + "_" + signed(tile.x()) + "_" + signed(tile.z());
    }

    private static String signed(int value) {
        return value < 0 ? "n" + -(long) value : "p" + value;
    }
}
