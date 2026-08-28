package dev.bluemapminimap.model;

public record FetchResult(Status status, byte[] body, ResourceValidators validators, String contentType) {
    public enum Status {
        OK,
        NOT_MODIFIED,
        MISSING
    }

    public static FetchResult missing() {
        return new FetchResult(Status.MISSING, new byte[0], ResourceValidators.NONE, "");
    }
}
