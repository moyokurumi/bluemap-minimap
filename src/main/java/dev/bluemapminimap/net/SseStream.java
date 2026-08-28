package dev.bluemapminimap.net;

import java.io.IOException;
import java.io.InputStream;

public final class SseStream implements AutoCloseable {
    private final InputStream input;

    public SseStream(InputStream input) {
        this.input = input;
    }

    public InputStream input() {
        return input;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
