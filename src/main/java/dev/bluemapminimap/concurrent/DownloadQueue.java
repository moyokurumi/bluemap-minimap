package dev.bluemapminimap.concurrent;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DownloadQueue implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Set<Object> pendingKeys = ConcurrentHashMap.newKeySet();

    public DownloadQueue(int concurrency, int capacity) {
        if (concurrency <= 0 || capacity <= 0) throw new IllegalArgumentException("invalid queue limits");
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "BlueMap-Minimap-Download-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(
                concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public Future<?> submitOnce(Object key, Runnable task) {
        if (!pendingKeys.add(key)) return null;
        try {
            return executor.submit(() -> {
                try {
                    task.run();
                } finally {
                    pendingKeys.remove(key);
                }
            });
        } catch (RejectedExecutionException ex) {
            pendingKeys.remove(key);
            return null;
        }
    }

    public int queuedCount() {
        return executor.getQueue().size();
    }

    public int activeCount() {
        return executor.getActiveCount();
    }

    public int pendingCount() {
        return pendingKeys.size();
    }

    public void cancelQueued() {
        executor.getQueue().clear();
        pendingKeys.clear();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        pendingKeys.clear();
    }
}
