package com.wifisync.util;

import java.io.File;

/**
 * Central place for every path AirMesh writes to on disk (config, lock file,
 * settings). Keeping this in one class means install scripts, the single
 * instance guard, and the settings manager can never disagree about where
 * things live.
 */
public final class AppPaths {
    private AppPaths() {}

    public static File getConfigDir() {
        File dir = new File(System.getProperty("user.home"), ".airmesh");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getSettingsFile() {
        return new File(getConfigDir(), "settings.properties");
    }

    public static File getLockFile() {
        return new File(getConfigDir(), "airmesh.lock");
    }
}
