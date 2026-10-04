package com.wifisync.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Prevents a second copy of AirMesh from starting on the same machine.
 *
 * On Linux this is what was causing the "multiple tray icons" annoyance:
 * launching the app manually while the systemd user-service copy is already
 * running (or restarting after a crash loop) left several JVMs alive, each
 * adding its own tray icon and its own UDP/TCP listeners fighting for the
 * same ports.
 *
 * We take an exclusive OS-level file lock on a small lock file in the config
 * dir. The lock is held for the lifetime of the process and is released
 * automatically by the OS if the process dies, so there's no stale-lock
 * cleanup to worry about.
 */
public final class SingleInstanceGuard {
    // Keep references alive for the whole process lifetime -- if these get
    // garbage collected the lock can be released early on some platforms.
    private static RandomAccessFile lockRaf;
    private static FileChannel lockChannel;
    private static FileLock lock;

    private SingleInstanceGuard() {}

    /**
     * @return true if this process acquired the lock (i.e. it is the only
     *         instance), false if another instance already holds it.
     */
    public static synchronized boolean tryAcquire() {
        try {
            File lockFile = AppPaths.getLockFile();
            lockRaf = new RandomAccessFile(lockFile, "rw");
            lockChannel = lockRaf.getChannel();
            lock = lockChannel.tryLock();

            if (lock == null) {
                // Someone else holds it.
                closeQuietly();
                return false;
            }

            Runtime.getRuntime().addShutdownHook(new Thread(SingleInstanceGuard::release, "SingleInstanceReleaseHook"));
            return true;
        } catch (Exception e) {
            // If we can't even determine lock state, fail open rather than
            // blocking the user from starting the app at all.
            Logger.error("SINGLETON", "Could not acquire single-instance lock, continuing anyway", e);
            return true;
        }
    }

    private static synchronized void release() {
        try {
            if (lock != null) lock.release();
        } catch (Exception ignored) {}
        closeQuietly();
    }

    private static void closeQuietly() {
        try { if (lockChannel != null) lockChannel.close(); } catch (Exception ignored) {}
        try { if (lockRaf != null) lockRaf.close(); } catch (Exception ignored) {}
    }
}
