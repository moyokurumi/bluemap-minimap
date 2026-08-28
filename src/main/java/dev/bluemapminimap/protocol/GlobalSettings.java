package dev.bluemapminimap.protocol;

import java.util.List;

public record GlobalSettings(String mapDataRoot, String liveDataRoot, List<String> maps) {
}
