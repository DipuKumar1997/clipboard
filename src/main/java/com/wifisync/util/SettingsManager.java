package com.wifisync.util;

import java.io.*;
import java.util.Properties;

/**
 * Simple persisted key/value settings store backed by a .properties file in
 * the user's config dir (~/.airmesh/settings.properties). Currently only
 * holds the download folder, but is written generically so new settings
 * (e.g. a display name, or "always accept from trusted peers") can be added
 * without touching callers.
 */
public class SettingsManager {
    private static final String KEY_DOWNLOAD_DIR = "download.dir";

    private final File settingsFile;
    private final Properties props = new Properties();

    public SettingsManager() {
        this.settingsFile = AppPaths.getSettingsFile();
        load();
    }

    private void load() {
        if (!settingsFile.exists()) return;
        try (InputStream in = new FileInputStream(settingsFile)) {
            props.load(in);
        } catch (IOException e) {
            Logger.error("SETTINGS", "Failed to load settings file", e);
        }
    }

    private void save() {
        try (OutputStream out = new FileOutputStream(settingsFile)) {
            props.store(out, "AirMesh settings");
        } catch (IOException e) {
            Logger.error("SETTINGS", "Failed to save settings file", e);
        }
    }

    public synchronized String getDownloadDir() {
        String configured = props.getProperty(KEY_DOWNLOAD_DIR);
        if (configured != null && !configured.trim().isEmpty()) {
            File f = new File(configured);
            if (f.exists() && f.isDirectory()) {
                return configured;
            }
        }
        return new File(System.getProperty("user.home"), "Downloads").getAbsolutePath();
    }

    public synchronized void setDownloadDir(String path) {
        props.setProperty(KEY_DOWNLOAD_DIR, path);
        save();
        Logger.log("SETTINGS", "Download folder set to: " + path);
    }
}
