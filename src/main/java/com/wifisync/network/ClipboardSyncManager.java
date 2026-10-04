package com.wifisync.network;

import com.wifisync.service.DeduplicationService;
import com.wifisync.util.Logger;

import java.io.DataOutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Delivers clipboard text to peers over two independent channels at once:
 *
 *   1. TCP (reliable, ordered) on the existing port 8889 -- same wire
 *      format NetworkServer already understands, with a couple of retries
 *      if the peer is briefly unreachable.
 *   2. UDP (fire-and-forget, low latency) on a dedicated port 8890 -- a
 *      speculative duplicate that can arrive even if the TCP attempt is
 *      still retrying.
 *
 * Whichever copy arrives first "wins"; the other is recognized as a
 * duplicate (by content + a small time bucket) and dropped, so the user
 * never sees a double-paste. This is the "two or three parallel paths, no
 * single point of freeze" delivery model.
 *
 * File transfers deliberately are NOT sent this way -- they stay TCP-only
 * in NetworkClient/NetworkServer, because reassembling large binary
 * transfers from an unreliable UDP stream needs its own ack/sequencing
 * protocol, and getting that wrong silently corrupts files. Clipboard text
 * is small and idempotent, which is what makes the dual-path trick safe.
 */
public class ClipboardSyncManager {
    private static final int TCP_PORT = 8889;
    private static final int UDP_FAST_PORT = 8890;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 700;

    private final ExecutorService sendExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ClipboardSyncSender");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ClipboardSyncRetry");
        t.setDaemon(true);
        return t;
    });

    private final DeduplicationService dedup = new DeduplicationService();
    private DatagramSocket udpSocket;
    private volatile boolean running = false;

    private final BiConsumer<String, String> onDeliveredCallback; // (text, sourceTransport)

    public ClipboardSyncManager(BiConsumer<String, String> onDeliveredCallback) {
        this.onDeliveredCallback = onDeliveredCallback;
    }

    public void start() {
        running = true;
        new Thread(this::runUdpListener, "ClipboardUdpFastListener").start();
    }

    public void stop() {
        running = false;
        if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
        sendExecutor.shutdownNow();
        retryScheduler.shutdownNow();
    }

    /** Sends the given clipboard text to every target IP, over both channels in parallel. */
    public void sendToPeers(String text, List<String> targetIps) {
        for (String ip : targetIps) {
            sendExecutor.execute(() -> sendUdpFast(ip, text));
            sendExecutor.execute(() -> sendTcpReliable(ip, text, MAX_RETRIES));
        }
    }

    /** Called by NetworkServer when a "CLIPBOARD" TCP payload arrives. */
    public void handleTcpReceived(String text, String fromIp) {
        applyIfNew(text, "TCP");
    }

    private void runUdpListener() {
        try {
            udpSocket = new DatagramSocket(null);
            udpSocket.setReuseAddress(true);
            udpSocket.bind(new InetSocketAddress("0.0.0.0", UDP_FAST_PORT));
            Logger.log("CLIP-UDP", "Speculative clipboard UDP listener bound on port " + UDP_FAST_PORT);

            byte[] buffer = new byte[65535];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                applyIfNew(text, "UDP");
            }
        } catch (Exception e) {
            if (running) {
                Logger.error("CLIP-UDP", "Speculative UDP listener stopped unexpectedly", e);
            }
        }
    }

    private void sendUdpFast(String ip, String text) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(ip);
            socket.send(new DatagramPacket(data, data.length, addr, UDP_FAST_PORT));
        } catch (Exception ignored) {
            // Best-effort path; the TCP path is the one that guarantees delivery.
        }
    }

    private void sendTcpReliable(String ip, String text, int retriesLeft) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, TCP_PORT), 3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("CLIPBOARD");
            out.writeUTF(text);
            out.flush();
            Logger.log("TCP-CLIENT", "Dispatched clipboard content to " + ip);
        } catch (Exception e) {
            if (retriesLeft > 0) {
                Logger.log("TCP-CLIENT", "Clipboard send to " + ip + " failed, retrying (" + retriesLeft + " left)");
                retryScheduler.schedule(() -> sendTcpReliable(ip, text, retriesLeft - 1), RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
            } else {
                Logger.error("TCP-CLIENT", "Failed sending clipboard data to " + ip + " after retries", e);
            }
        }
    }

    private void applyIfNew(String text, String transport) {
        if (text == null || text.trim().isEmpty()) return;
        // Dedup key buckets identical content by 2-second windows: the TCP
        // and UDP copies of the *same* user action land within that window
        // and are treated as one delivery, while a genuine repeat copy made
        // later still gets synced.
        String key = text.hashCode() + "-" + (System.currentTimeMillis() / 2000);
        if (dedup.isNewAndMark(key)) {
            Logger.log("CLIP-SYNC", "Delivered clipboard update via " + transport);
            if (onDeliveredCallback != null) {
                onDeliveredCallback.accept(text, transport);
            }
        } else {
            Logger.log("CLIP-SYNC", "Duplicate clipboard update via " + transport + " ignored");
        }
    }
}
