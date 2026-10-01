package com.wifisync.network;

import com.wifisync.model.Peer;
import com.wifisync.util.Logger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class DiscoveryService {
    private static final int DISCOVERY_PORT = 8888;
    private static final String REQ_PREFIX = "WIFI_SYNC_DISCOVER_REQ";
    private static final String RESP_PREFIX = "WIFI_SYNC_DISCOVER_RESP:";

    private DatagramSocket socket;
    private boolean running = false;
    private final PeerDiscoveryListener listener;
    private final String localHostName;

    public interface PeerDiscoveryListener {
        void onPeerDiscovered(Peer peer);
    }

    public DiscoveryService(PeerDiscoveryListener listener) {
        this.listener = listener;
        String host = "Unknown-Host";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            Logger.error("DISCOVERY", "Could not resolve local host name", e);
        }
        this.localHostName = host;
        Logger.log("DISCOVERY", "Local host identified as: " + this.localHostName);
    }

    public void startListener() {
        running = true;
        new Thread(() -> {
            try {
                socket = new DatagramSocket(DISCOVERY_PORT);
                socket.setBroadcast(true);
                Logger.log("DISCOVERY-UDP", "UDP Discovery Listener bound successfully to port " + DISCOVERY_PORT);
                byte[] buffer = new byte[1024];

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    String senderIp = packet.getAddress().getHostAddress();

                    if (isLocalIp(packet.getAddress())) continue;

                    if (message.equals(REQ_PREFIX)) {
                        String response = RESP_PREFIX + localHostName;
                        byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket respPacket = new DatagramPacket(respBytes, respBytes.length, packet.getAddress(), DISCOVERY_PORT);
                        socket.send(respPacket);
                    } else if (message.startsWith(RESP_PREFIX)) {
                        String peerHost = message.substring(RESP_PREFIX.length());
                        listener.onPeerDiscovered(new Peer(senderIp, peerHost));
                    }
                }
            } catch (Exception e) {
                if (!running) Logger.log("DISCOVERY-UDP", "UDP Listener socket closed.");
            }
        }, "DiscoveryListenerThread").start();
    }

    public void sendBroadcastDiscovery() {
        new Thread(() -> {
            Set<InetAddress> broadcastTargets = new HashSet<>();
            try (DatagramSocket broadcastSocket = new DatagramSocket()) {
                broadcastSocket.setBroadcast(true);
                byte[] requestData = REQ_PREFIX.getBytes(StandardCharsets.UTF_8);

                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces != null && interfaces.hasMoreElements()) {
                    NetworkInterface netIf = interfaces.nextElement();
                    if (!netIf.isUp() || netIf.isLoopback()) continue;

                    for (InterfaceAddress ifAddr : netIf.getInterfaceAddresses()) {
                        InetAddress broadcast = ifAddr.getBroadcast();
                        if (broadcast != null) broadcastTargets.add(broadcast);
                    }
                }

                try { broadcastTargets.add(InetAddress.getByName("255.255.255.255")); } catch (Exception ignored) {}

                for (InetAddress target : broadcastTargets) {
                    try {
                        DatagramPacket packet = new DatagramPacket(requestData, requestData.length, target, DISCOVERY_PORT);
                        broadcastSocket.send(packet);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                Logger.error("DISCOVERY-SCAN", "Error during discovery scan", e);
            }
        }, "BroadcastSenderThread").start();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }

    private boolean isLocalIp(InetAddress addr) {
        try {
            if (addr.isLoopbackAddress()) return true;
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (Exception e) {
            return false;
        }
    }
}