package dev.bluemapminimap.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerProfile {
    public String blueMapUrl = "";
    public Map<String, String> dimensions = new LinkedHashMap<>();

    public ServerProfile copy() {
        ServerProfile copy = new ServerProfile();
        copy.blueMapUrl = blueMapUrl;
        copy.dimensions = new LinkedHashMap<>(dimensions);
        return copy;
    }
}
