package dev.bluemapminimap.protocol;

import dev.bluemapminimap.config.ServerProfile;

import java.util.List;

public final class MapSelection {
    private MapSelection() {
    }

    public static String choose(String dimension, ServerProfile profile, List<String> publishedMaps) {
        String explicit = profile.dimensions.get(dimension);
        if (explicit != null && publishedMaps.contains(explicit)) return explicit;
        if (!"minecraft:overworld".equals(dimension)) return null;
        if (publishedMaps.contains("overworld")) return "overworld";
        if (publishedMaps.contains("world")) return "world";
        return publishedMaps.size() == 1 ? publishedMaps.getFirst() : null;
    }
}
