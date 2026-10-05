package dev.bluemapminimap.config;

import com.google.gson.annotations.SerializedName;

public enum MapOrientation {
    @SerializedName(value = "north_up", alternate = {"NORTH_UP"})
    NORTH_UP,
    @SerializedName(value = "heading_up", alternate = {"HEADING_UP"})
    HEADING_UP;

    public MapOrientation next() {
        return this == NORTH_UP ? HEADING_UP : NORTH_UP;
    }
}
