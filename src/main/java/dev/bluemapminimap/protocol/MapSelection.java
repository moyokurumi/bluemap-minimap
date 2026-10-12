package dev.bluemapminimap.protocol;

import dev.bluemapminimap.config.ServerProfile;

import java.util.List;

public final class MapSelection {
    private MapSelection() {
    }

    public static String choose(String dimension, ServerProfile profile, List<String> publishedMaps) {
        String explicit = profile.dimensions.get(dimension);
        // An explicit choice remains authoritative even when it is unavailable.
        if (explicit != null) return publishedMaps.contains(explicit) ? explicit : null;
        List<String> conventional = switch (dimension) {
            case "minecraft:overworld" -> List.of("overworld", "world");
            case "minecraft:the_nether" -> List.of("the_nether", "world_nether", "nether");
            case "minecraft:the_end" -> List.of("the_end", "world_the_end", "end");
            default -> List.of();
        };
        List<String> matches = conventional.stream().filter(publishedMaps::contains).toList();
        // Unknown dimensions and ambiguous names must never fall back to an arbitrary map.
        return matches.size() == 1 ? matches.getFirst() : null;
    }
}
