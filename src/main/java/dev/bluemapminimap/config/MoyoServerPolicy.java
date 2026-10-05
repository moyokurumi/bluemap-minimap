package dev.bluemapminimap.config;

import java.net.URI;
import java.util.List;

/** Built-in participant configuration. Never discovers endpoints on other servers. */
public final class MoyoServerPolicy {
    public static final String SERVER = "mc.moyokurumi.com";
    public static final String BLUE_MAP = "https://mcmap.moyokurumi.com/";

    private MoyoServerPolicy() { }

    public static boolean supports(String address) {
        return address != null && SERVER.equals(MinimapConfig.normalizeServerAddress(address));
    }

    public static ServerProfile defaults() {
        ServerProfile profile = new ServerProfile();
        profile.blueMapUrl = BLUE_MAP;
        profile.dimensions.put("minecraft:overworld", "overworld");
        profile.dimensions.put("minecraft:world", "overworld");
        profile.dimensions.put("minecraft:resource", "resource");
        return profile;
    }

    /** Overlay missing defaults in memory; never rewrite a user's saved profile. */
    public static ServerProfile profileFor(MinimapConfig config, String address) {
        if (!supports(address)) return null;
        ServerProfile saved = config.profileFor(address);
        if (saved == null) return defaults();
        ServerProfile effective = saved.copy();
        if (effective.blueMapUrl == null || effective.blueMapUrl.isBlank()) effective.blueMapUrl = BLUE_MAP;
        if (isPublicMap(effective.blueMapUrl)) {
            defaults().dimensions.forEach(effective.dimensions::putIfAbsent);
        }
        return effective;
    }

    public static boolean isPublicMap(String raw) {
        try {
            URI uri = URI.create(raw.strip());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "mcmap.moyokurumi.com".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** No overworld/single-map guessing for unknown worlds or missing explicit maps. */
    public static String mapFor(String dimension, ServerProfile profile, List<String> publishedMaps) {
        String mapped = profile.dimensions.get(dimension);
        return mapped != null && publishedMaps.contains(mapped) ? mapped : null;
    }
}
