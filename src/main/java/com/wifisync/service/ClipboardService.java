package com.wifisync.service;

import com.wifisync.util.Logger;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class ClipboardService implements FlavorListener {
    private final Clipboard clipboard;
    private String lastTextContent = "";
    private volatile boolean isSelfUpdating = false;
    private Consumer<String> onTextClipboardChanged;
    private Consumer<List<File>> onFileClipboardChanged;

    public ClipboardService() {
        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    public void startListening(Consumer<String> textCallback, Consumer<List<File>> fileCallback) {
        this.onTextClipboardChanged = textCallback;
        this.onFileClipboardChanged = fileCallback;
        try {
            this.clipboard.addFlavorListener(this);
        } catch (Exception e) {
            Logger.error("CLIPBOARD", "Error adding flavor listener", e);
        }
        this.lastTextContent = getCurrentText();
        Logger.log("CLIPBOARD", "Started clipboard listener daemon.");
    }

    public void stopListening() {
        try {
            this.clipboard.removeFlavorListener(this);
        } catch (Exception ignored) {}
        Logger.log("CLIPBOARD", "Stopped clipboard listener.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void flavorsChanged(FlavorEvent e) {
        if (isSelfUpdating) {
            isSelfUpdating = false;
            return;
        }

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
            if (currentText != null && !currentText.equals(lastTextContent) && !currentText.trim().isEmpty()) {
                this.lastTextContent = currentText;
                Logger.log("CLIPBOARD", "Local text copy detected! Content: \"" + truncate(currentText) + "\"");
                if (onTextClipboardChanged != null) {
                    onTextClipboardChanged.accept(currentText);
                }
            }
        } catch (Exception ex) {
            Logger.error("CLIPBOARD", "Error processing clipboard flavor change", ex);
        }
    }

    public synchronized void setClipboardTextContent(String text) {
        if (text == null || text.equals(lastTextContent)) return;
        this.isSelfUpdating = true;
        this.lastTextContent = text;
        try {
            StringSelection selection = new StringSelection(text);
            this.clipboard.setContents(selection, selection);
            Logger.log("CLIPBOARD", "System clipboard updated: \"" + truncate(text) + "\"");
        } catch (Exception e) {
            Logger.error("CLIPBOARD", "Failed to update system clipboard", e);
            this.isSelfUpdating = false;
        }
    }

    private String getCurrentText() {
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return (String) clipboard.getData(DataFlavor.stringFlavor);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String truncate(String text) {
        return text.length() > 30 ? text.substring(0, 30) + "..." : text;
    }
}