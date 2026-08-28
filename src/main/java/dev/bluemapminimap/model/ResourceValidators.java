package dev.bluemapminimap.model;

public record ResourceValidators(String etag, String lastModified) {
    public static final ResourceValidators NONE = new ResourceValidators(null, null);
}
