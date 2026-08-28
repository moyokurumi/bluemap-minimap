package dev.bluemapminimap.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bluemapminimap.model.RemotePlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BlueMapJson {
    private BlueMapJson() {
    }

    public static GlobalSettings parseGlobalSettings(byte[] bytes) {
        JsonObject root = parseObject(bytes);
        String mapDataRoot = stringOr(root, "mapDataRoot", "maps");
        String liveDataRoot = stringOr(root, "liveDataRoot", "maps");
        JsonArray mapsArray = root.has("maps") && root.get("maps").isJsonArray()
                ? root.getAsJsonArray("maps")
                : new JsonArray();
        List<String> maps = new ArrayList<>();
        for (JsonElement item : mapsArray) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) maps.add(item.getAsString());
        }
        return new GlobalSettings(mapDataRoot, liveDataRoot, List.copyOf(maps));
    }

    public static int[] parseLowresLayout(byte[] bytes) {
        JsonObject root = parseObject(bytes);
        JsonObject lowres = root.getAsJsonObject("lowres");
        if (lowres == null) throw new IllegalArgumentException("BlueMap map settings do not contain lowres");
        JsonArray tileSize = lowres.getAsJsonArray("tileSize");
        if (tileSize == null || tileSize.size() < 2) throw new IllegalArgumentException("BlueMap lowres.tileSize is invalid");
        int x = tileSize.get(0).getAsInt();
        int z = tileSize.get(1).getAsInt();
        int factor = lowres.has("lodFactor") ? lowres.get("lodFactor").getAsInt() : 2;
        int count = lowres.has("lodCount") ? lowres.get("lodCount").getAsInt() : 1;
        if (x <= 0 || z <= 0 || factor <= 0 || count <= 0) throw new IllegalArgumentException("BlueMap lowres layout is invalid");
        return new int[]{x, z, factor, count};
    }

    public static List<RemotePlayer> parsePlayers(byte[] bytes) {
        JsonObject root = parseObject(bytes);
        JsonArray players = root.has("players") && root.get("players").isJsonArray()
                ? root.getAsJsonArray("players")
                : new JsonArray();
        List<RemotePlayer> result = new ArrayList<>();
        for (JsonElement item : players) {
            if (!item.isJsonObject()) continue;
            JsonObject player = item.getAsJsonObject();
            try {
                JsonObject position = player.getAsJsonObject("position");
                JsonObject rotation = player.getAsJsonObject("rotation");
                result.add(new RemotePlayer(
                        UUID.fromString(player.get("uuid").getAsString()),
                        player.get("name").getAsString(),
                        position.get("x").getAsDouble(),
                        position.get("y").getAsDouble(),
                        position.get("z").getAsDouble(),
                        rotation == null || !rotation.has("yaw") ? 0.0F : rotation.get("yaw").getAsFloat(),
                        player.has("foreign") && player.get("foreign").getAsBoolean()
                ));
            } catch (RuntimeException ignored) {
                // Ignore one malformed player without discarding the valid list.
            }
        }
        return List.copyOf(result);
    }

    public static TileEvent parseTileEvent(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return new TileEvent(root.get("x").getAsInt(), root.get("y").getAsInt(), root.get("lod").getAsInt());
    }

    private static JsonObject parseObject(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String stringOr(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
}
