package dev.bluemapminimap.protocol;

import java.util.function.BiConsumer;

public final class SseParser {
    private final BiConsumer<String, String> consumer;
    private String event = "message";
    private final StringBuilder data = new StringBuilder();

    public SseParser(BiConsumer<String, String> consumer) {
        this.consumer = consumer;
    }

    public void acceptLine(String line) {
        if (line.isEmpty()) {
            dispatch();
            return;
        }
        if (line.startsWith(":")) return;
        int separator = line.indexOf(':');
        String field = separator < 0 ? line : line.substring(0, separator);
        String value = separator < 0 ? "" : line.substring(separator + 1);
        if (value.startsWith(" ")) value = value.substring(1);
        switch (field) {
            case "event" -> event = value;
            case "data" -> {
                if (!data.isEmpty()) data.append('\n');
                data.append(value);
            }
            default -> {
                // id and retry are intentionally ignored.
            }
        }
    }

    public void finish() {
        dispatch();
    }

    private void dispatch() {
        if (!data.isEmpty()) consumer.accept(event, data.toString());
        event = "message";
        data.setLength(0);
    }
}
