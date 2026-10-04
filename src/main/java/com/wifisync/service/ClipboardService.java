package com.wifisync.service;

import com.wifisync.util.Logger;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Watches the system clipboard and reports local changes, while also being
 * able to push remote content into it without re-broadcasting that same
 * content right back out.
 *
 * IMPORTANT: the previous version used a one-shot boolean ("isSelfUpdating")
 * that got consumed the next time a flavor-change event fired. If that event
 * ever failed to fire immediately after we programmatically set the
 * clipboard (which happens intermittently -- AWT's clipboard-change
 * notification isn't guaranteed to be synchronous or even guaranteed to fire
 * once per change on every platform), the flag stayed stuck "true" and
 * silently swallowed the *next real local copy* the user made. That was the
 * "copy once, nothing happens, copy again to un-stick it" bug. This version
 * compares against the last value we know about instead of consuming a flag,
 * so there is nothing to get stuck.
 */
public class ClipboardService implements FlavorListener {
    private final Clipboard clipboard;

    // The last text we know is actually sitting in the OS clipboard.
    private volatile String lastKnownContent = "";

    // The last text WE pushed into the clipboard on behalf of a remote peer.
    // If the next flavor-change event reports exactly this text, it is an
    // echo of our own write, not a new local copy -- ignore it. This is a
    // plain value comparison, not a consumed flag, so a missed or delayed
    // event can never leave us in a bad state.
    private volatile String lastRemoteAppliedContent = null;

    private Consumer<String> onTextClipboardChanged;
    private Consumer<List<File>> onFileClipboardChanged;

    public ClipboardService() {
        Clipboard cb;
        try {
            cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (Throwable t) {
            // On macOS this can fail if the process isn't allowed to touch
            // the window server / pasteboard yet (e.g. certain sandboxing or
            // launch contexts). Log something actionable instead of just
            // crashing the constructor.
            Logger.error("CLIPBOARD", "Unable to access system clipboard. On macOS, make sure AirMesh " +
                    "was launched in a normal user (Aqua) session and isn't blocked under " +
                    "System Settings > Privacy & Security.", t);
            cb = null;
        }
        this.clipboard = cb;
    }

    public void startListening(Consumer<String> textCallback, Consumer<List<File>> fileCallback) {
        this.onTextClipboardChanged = textCallback;
        this.onFileClipboardChanged = fileCallback;
        if (clipboard == null) {
            Logger.error("CLIPBOARD", "Clipboard unavailable; sync disabled for this session.", null);
            return;
        }
        try {
            this.clipboard.addFlavorListener(this);
        } catch (Exception e) {
            Logger.error("CLIPBOARD", "Error adding flavor listener", e);
        }
        String current = getCurrentText();
        this.lastKnownContent = current != null ? current : "";
        Logger.log("CLIPBOARD", "Started clipboard listener daemon.");
    }

    public void stopListening() {
        if (clipboard == null) return;
        try {
            this.clipboard.removeFlavorListener(this);
        } catch (Exception ignored) {}
        Logger.log("CLIPBOARD", "Stopped clipboard listener.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void flavorsChanged(FlavorEvent e) {
        if (clipboard == null) return;
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                List<File> copiedFiles = (List<File>) clipboard.getData(DataFlavor.javaFileListFlavor);
                if (copiedFiles != null && !copiedFiles.isEmpty()) {
                    Logger.log("CLIPBOARD", "Local File/Folder Copy Detected: " + copiedFiles.size() + " item(s)");
                    if (onFileClipboardChanged != null) {
                        onFileClipboardChanged.accept(copiedFiles);
                    }
                    return;
                }
            }

            String currentText = getCurrentText();
            if (currentText == null || currentText.trim().isEmpty()) return;
            if (currentText.equals(lastKnownContent)) return; // no actual change

            lastKnownContent = currentText;

            if (currentText.equals(lastRemoteAppliedContent)) {
                // This is the clipboard settling after WE wrote it a moment
                // ago on behalf of a peer -- not a new local copy.
                Logger.log("CLIPBOARD", "Ignoring self-originated echo.");
                return;
            }

            Logger.log("CLIPBOARD", "Local text copy detected! Content: \"" + truncate(currentText) + "\"");
            if (onTextClipboardChanged != null) {
                onTextClipboardChanged.accept(currentText);
            }
        } catch (Exception ex) {
            Logger.error("CLIPBOARD", "Error processing clipboard flavor change", ex);
        }
    }

    /** Pushes remote clipboard content into the local system clipboard without re-triggering a broadcast. */
    public synchronized void setClipboardTextContent(String text) {
        if (clipboard == null || text == null || text.equals(lastKnownContent)) return;
        lastRemoteAppliedContent = text;
        lastKnownContent = text;
        try {
            StringSelection selection = new StringSelection(text);
            this.clipboard.setContents(selection, selection);
            Logger.log("CLIPBOARD", "System clipboard updated: \"" + truncate(text) + "\"");
        } catch (Exception e) {
            Logger.error("CLIPBOARD", "Failed to update system clipboard", e);
        }
    }

    private String getCurrentText() {
        try {
            if (clipboard != null && clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return (String) clipboard.getData(DataFlavor.stringFlavor);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String truncate(String text) {
        return text.length() > 30 ? text.substring(0, 30) + "..." : text;
    }
}
