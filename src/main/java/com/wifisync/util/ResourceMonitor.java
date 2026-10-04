package com.wifisync.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

/**
 * Samples JVM heap usage on a single background daemon thread and reports it
 * periodically. This is intentionally cheap: one scheduled task, no
 * allocation-heavy work, so it doesn't itself become the thing making the
 * app "heavier".
 */
public class ResourceMonitor {
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ResourceMonitorThread");
                t.setDaemon(true);
                return t;
            });

    private volatile boolean started = false;

    /**
     * @param usedBytesCallback called every intervalSeconds with the
     *                          currently used heap size, in bytes.
     */
    public synchronized void start(int intervalSeconds, LongConsumer usedBytesCallback) {
        if (started) return;
        started = true;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Runtime rt = Runtime.getRuntime();
                long used = rt.totalMemory() - rt.freeMemory();
                usedBytesCallback.accept(used);
            } catch (Exception ignored) {}
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
