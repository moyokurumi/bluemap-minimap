package dev.bluemapminimap.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class MemoryCache<K, V> {
    private final int capacity;
    private final BiConsumer<K, V> evictionListener;
    private final LinkedHashMap<K, V> values = new LinkedHashMap<>(16, 0.75F, true);

    public MemoryCache(int capacity, BiConsumer<K, V> evictionListener) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.evictionListener = evictionListener;
    }

    public synchronized V get(K key) {
        return values.get(key);
    }

    public synchronized void put(K key, V value) {
        V previous = values.put(key, value);
        if (previous != null && previous != value) evictionListener.accept(key, previous);
        while (values.size() > capacity) {
            Map.Entry<K, V> eldest = values.entrySet().iterator().next();
            values.remove(eldest.getKey());
            evictionListener.accept(eldest.getKey(), eldest.getValue());
        }
    }

    public synchronized int size() {
        return values.size();
    }

    public synchronized void clear() {
        values.forEach(evictionListener);
        values.clear();
    }
}
