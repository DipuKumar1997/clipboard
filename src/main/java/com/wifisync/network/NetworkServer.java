package com.wifisync.network;

import com.wifisync.util.Logger;
import java.io.*;
import java.net.*;

public class NetworkServer {
    private static final int TCP_PORT = 8889;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final MessageHandler handler;

    public interface MessageHandler {
        void onClipboardReceived(String text, String fromIp);
        boolean onFilePermissionRequested(String fileName, long fileSize, String fromIp);
        void onFileReceived(String fileName, byte[] data, String fromIp);
    }

    public NetworkServer(MessageHandler handler) {
        this.handler = handler;
    }

    public void start() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                Logger.log("TCP-SERVER", "TCP Receiver Server listening on port " + TCP_PORT);
                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                }
            } catch (Exception e) {
                if (running) {
                    Logger.error("TCP-SERVER", "Exception in TCP Server accept loop", e);
                }
            }
        }, "NetworkServerThread").start();
    }

    private void handleClient(Socket socket) {
        new Thread(() -> {
            String fromIp = socket.getInetAddress().getHostAddress();
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                
                String type = in.readUTF();

                if ("CLIPBOARD".equals(type)) {
                    String text = in.readUTF();
                    Logger.log("TCP-SERVER", "Received CLIPBOARD payload from " + fromIp);
                    handler.onClipboardReceived(text, fromIp);
                } else if ("FILE".equals(type)) {
                    String fileName = in.readUTF();
                    long fileSize = in.readLong();
                    
                    boolean approved = handler.onFilePermissionRequested(fileName, fileSize, fromIp);
                    
                    if (!approved) {
                        out.writeUTF("REJECTED");
                        out.flush();
                        Logger.log("TCP-SERVER", "User declined incoming file: " + fileName + " from " + fromIp);
                        return;
                    }

                    out.writeUTF("ACCEPTED");
                    out.flush();

                    Logger.log("TCP-SERVER", String.format("Receiving FILE '%s' (%.2f MB) from %s", 
                        fileName, fileSize / (1024.0 * 1024.0), fromIp));
                    
                    byte[] fileBytes = new byte[(int) fileSize];
                    in.readFully(fileBytes);
                    Logger.log("TCP-SERVER", "Successfully received file '" + fileName + "' from " + fromIp);
                    handler.onFileReceived(fileName, fileBytes, fromIp);
                }
            } catch (Exception e) {
                Logger.error("TCP-SERVER", "Error handling client payload from " + fromIp, e);
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }, "ClientHandlerThread").start();
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
    }
}