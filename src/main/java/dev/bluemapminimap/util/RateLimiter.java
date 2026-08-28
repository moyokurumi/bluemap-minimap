package dev.bluemapminimap.util;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
    private final long intervalNanos;
    private final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();

    public RateLimiter(Duration interval) {
        this.intervalNanos = interval.toNanos();
    }

    public boolean allow(String category) {
        long now = System.nanoTime();
        Long previous = last.put(category, now);
        if (previous == null || now - previous >= intervalNanos) return true;
        last.put(category, previous);
        return false;
    }
}
