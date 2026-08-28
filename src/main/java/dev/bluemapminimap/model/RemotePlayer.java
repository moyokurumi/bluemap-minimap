package dev.bluemapminimap.model;

import java.util.UUID;

public record RemotePlayer(UUID uuid, String name, double x, double y, double z, float yaw, boolean foreign) {
}
