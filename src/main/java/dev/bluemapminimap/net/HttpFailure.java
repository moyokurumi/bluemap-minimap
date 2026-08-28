package dev.bluemapminimap.net;

import java.io.IOException;

public final class HttpFailure extends IOException {
    public enum Category {
        INVALID_URI,
        CROSS_HOST_REDIRECT,
        TOO_MANY_REDIRECTS,
        RESPONSE_TOO_LARGE,
        UNSUPPORTED_CONTENT,
        HTTP_STATUS,
        TIMEOUT,
        IO
    }

    private final Category category;
    private final int statusCode;

    public HttpFailure(Category category, String safeMessage) {
        this(category, safeMessage, -1, null);
    }

    public HttpFailure(Category category, String safeMessage, int statusCode, Throwable cause) {
        super(safeMessage, cause);
        this.category = category;
        this.statusCode = statusCode;
    }

    public Category category() {
        return category;
    }

    public int statusCode() {
        return statusCode;
    }
}
