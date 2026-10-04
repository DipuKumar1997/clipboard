package com.wifisync.network;

import com.wifisync.util.FolderZipper;
import com.wifisync.util.Logger;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkClient {
    private static final int TCP_PORT = 8889;
    private static final int MAX_READ_ATTEMPTS = 4;

    // A small shared pool instead of an unbounded "new Thread per send" --
    // keeps memory/thread overhead bounded even if the user fires off a lot
    // of copies or files in a burst.
    private static final ExecutorService FILE_SENDER_POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "FileSenderThread");
        t.setDaemon(true);
        return t;
    });

    public static void sendFile(String targetIp, File file) {
        FILE_SENDER_POOL.execute(() -> {
            if (file.isDirectory()) {
                sendFolder(targetIp, file);
            } else {
                sendSingleFileWithRetry(targetIp, file, file.getName());
            }
        });
    }

    private static void sendFolder(String targetIp, File folder) {
        File zipTemp = null;
        try {
            zipTemp = FolderZipper.zipDirectoryToTemp(folder);
            String wireName = folder.getName() + FolderZipper.FOLDER_MARKER_SUFFIX;
            Logger.log("TCP-CLIENT", "Packed folder '" + folder.getName() + "' for transfer to " + targetIp);
            sendSingleFileWithRetry(targetIp, zipTemp, wireName);
        } catch (IOException e) {
            Logger.error("TCP-CLIENT", "Failed to package folder '" + folder.getName() + "' for transfer", e);
        } finally {
            if (zipTemp != null) {
                zipTemp.delete();
            }
        }
    }

    /**
     * Sends a single file, retrying the read+transmit a few times on
     * transient IO errors (e.g. macOS "Resource deadlock avoided" /
     * EDEADLK, which happens when the source file is still being flushed or
     * coordinated by Finder/the owning app right as we try to read it --
     * it typically clears up within a second).
     */
    private static void sendSingleFileWithRetry(String targetIp, File file, String wireName) {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_READ_ATTEMPTS; attempt++) {
            try {
                doSend(targetIp, file, wireName);
                return; // success
            } catch (IOException e) {
                lastError = e;
                boolean transientLooking = isLikelyTransient(e);
                Logger.log("TCP-CLIENT", String.format(
                        "Attempt %d/%d sending '%s' to %s failed (%s)%s",
                        attempt, MAX_READ_ATTEMPTS, wireName, targetIp, e.getMessage(),
                        transientLooking ? ", retrying..." : ""));
                if (!transientLooking) break;
                sleep(300L * attempt);
            }
        }
        Logger.error("TCP-CLIENT", "Failed sending file '" + wireName + "' to " + targetIp + " after " + MAX_READ_ATTEMPTS + " attempts", lastError);
    }

    private static boolean isLikelyTransient(IOException e) {
        String msg = e.getMessage();
        if (msg == null) return true; // unknown -- worth a retry, cheap to try
        String lower = msg.toLowerCase();
        return lower.contains("deadlock") || lower.contains("resource") || lower.contains("busy") || lower.contains("try again");
    }

    private static void doSend(String targetIp, File file, String wireName) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetIp, TCP_PORT), 5000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            out.writeUTF("FILE");
            out.writeUTF(wireName);
            out.writeLong(file.length());
            out.flush();

            String status = in.readUTF();
            if ("REJECTED".equals(status)) {
                Logger.log("TCP-CLIENT", "Transfer rejected by remote peer " + targetIp + " for file: " + wireName);
                return;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            out.flush();
            Logger.log("TCP-CLIENT", String.format("Successfully sent file '%s' to %s", wireName, targetIp));
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
