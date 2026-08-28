package dev.bluemapminimap.model;

public record ClientObservation(
        String serverAddress,
        String dimension,
        double x,
        double z,
        float yaw
) {
}
