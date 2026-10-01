package com.wifisync.network;

import com.wifisync.model.MessagePayload;
import com.wifisync.service.DeduplicationService;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class HybridNetworkManager {
    private static final int UDP_PORT = 8888;
    private static final int TCP_PORT = 8889;

    private final String localHostname;
    private final DeduplicationService deduplicationService = new DeduplicationService();
    private final Map<String, Boolean> ackTracker = new ConcurrentHashMap<>();
    private final Map<String, String> activePeers = new ConcurrentHashMap<>(); // Hostname -> IP
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private Consumer<String> onClipboardReceivedCallback;

    public HybridNetworkManager(String localHostname) {
        this.localHostname = localHostname;
    }

    public void setOnClipboardReceivedCallback(Consumer<String> callback) {
        this.onClipboardReceivedCallback = callback;
    }

    public void start() {
        startUdpListener();
        startTcpServer();
        startPeerDiscoveryAnnouncer();
    }

    // --- Parallel Send Engine ---
    public void sendClipboardParallel(String textContent) {
        MessagePayload msg = new MessagePayload(localHostname, "TEXT", textContent);
        byte[] rawBytes = msg.serialize().getBytes(StandardCharsets.UTF_8);

        for (Map.Entry<String, String> peer : activePeers.entrySet()) {
            String peerIp = peer.getValue();

            // 1. Speculative Fast Path (UDP)
            scheduler.execute(() -> sendUdpSpeculative(peerIp, rawBytes));

            // 2. Reliable Stream Path with ACK Retry Loop (TCP)
            scheduler.execute(() -> sendTcpReliableWithRetry(peerIp, msg, 3));
        }
    }

    private void sendUdpSpeculative(String ip, byte[] data) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(ip);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, UDP_PORT);
            socket.send(packet);
        } catch (Exception ignored) {}
    }

    private void sendTcpReliableWithRetry(String ip, MessagePayload msg, int retriesLeft) {
        if (retriesLeft <= 0 || ackTracker.getOrDefault(msg.messageId, false)) {
            ackTracker.remove(msg.messageId);
            return;
        }

        boolean success = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, TCP_PORT), 2000);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            writer.println(msg.serialize());
            success = true;
        } catch (Exception e) {
            System.err.println("[TCP-RETRY] Retry needed for " + ip + ", left: " + (retriesLeft - 1));
        }

        if (!success || !ackTracker.getOrDefault(msg.messageId, false)) {
            scheduler.schedule(() -> sendTcpReliableWithRetry(ip, msg, retriesLeft - 1), 500, TimeUnit.MILLISECONDS);
        }
    }

    // --- Server Listeners & Processors ---
    private void startTcpServer() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress("0.0.0.0", TCP_PORT));
                System.out.println("[HYBRID-NET] TCP Listener active on port " + TCP_PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    scheduler.execute(() -> handleTcpClient(clientSocket));
                }
            } catch (IOException e) {
                System.err.println("[HYBRID-NET] TCP Server error: " + e.getMessage());
            }
        });
    }

    private void handleTcpClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String raw = reader.readLine();
            if (raw != null) {
                processIncomingRawPayload(raw, socket.getInetAddress().getHostAddress(), true);
            }
        } catch (IOException ignored) {}
    }

    private void startUdpListener() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress("0.0.0.0", UDP_PORT));
                byte[] buffer = new byte[65535];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    processIncomingRawPayload(raw, packet.getAddress().getHostAddress(), false);
                }
            } catch (Exception e) {
                System.err.println("[HYBRID-NET] UDP Listener error: " + e.getMessage());
            }
        });
    }

    private void processIncomingRawPayload(String rawData, String senderIp, boolean isTcp) {
        if (rawData.startsWith("DISCOVER_REQ:")) {
            String senderName = rawData.substring("DISCOVER_REQ:".length());
            if (!senderName.equals(localHostname)) {
                activePeers.put(senderName, senderIp);
                sendUdpSpeculative(senderIp, ("DISCOVER_RESP:" + localHostname).getBytes(StandardCharsets.UTF_8));
            }
            return;
        }

        if (rawData.startsWith("DISCOVER_RESP:")) {
            String senderName = rawData.substring("DISCOVER_RESP:".length());
            if (!senderName.equals(localHostname)) {
                activePeers.put(senderName, senderIp);
            }
            return;
        }

        MessagePayload msg = MessagePayload.deserialize(rawData);
        if (msg == null || msg.senderId.equals(localHostname)) return;

        if ("ACK".equals(msg.type)) {
            ackTracker.put(msg.payload, true);
            return;
        }

        if ("TEXT".equals(msg.type)) {
            sendAckBack(senderIp, msg.messageId);

            if (deduplicationService.isNewAndMark(msg.messageId)) {
                System.out.println("[HYBRID-NET] Delivered new payload via " + (isTcp ? "TCP" : "UDP"));
                if (onClipboardReceivedCallback != null) {
                    onClipboardReceivedCallback.accept(msg.payload);
                }
            }
        }
    }

    private void sendAckBack(String targetIp, String messageId) {
        scheduler.execute(() -> {
            MessagePayload ack = new MessagePayload(localHostname, "ACK", messageId);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(targetIp, TCP_PORT), 1500);
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                writer.println(ack.serialize());
            } catch (Exception ignored) {}
        });
    }

    private void startPeerDiscoveryAnnouncer() {
        scheduler.scheduleAtFixedRate(() -> {
            byte[] req = ("DISCOVER_REQ:" + localHostname).getBytes(StandardCharsets.UTF_8);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                DatagramPacket packet = new DatagramPacket(req, req.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);
                socket.send(packet);
            } catch (Exception ignored) {}
        }, 0, 5, TimeUnit.SECONDS);
    }
}