package dev.bluemapminimap.config;

public enum HudPosition {
    TOP_RIGHT,
    TOP_LEFT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT;

    public HudPosition next() {
        HudPosition[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
