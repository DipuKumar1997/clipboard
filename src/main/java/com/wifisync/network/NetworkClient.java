package com.wifisync.network;

import com.wifisync.util.Logger;
import java.io.*;
import java.net.*;

public class NetworkClient {
    private static final int TCP_PORT = 8889;

    public static void sendClipboardText(String targetIp, String text) {
        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(targetIp, TCP_PORT), 3000);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF("CLIPBOARD");
                out.writeUTF(text);
                out.flush();
                Logger.log("TCP-CLIENT", "Dispatched clipboard content to " + targetIp);
            } catch (Exception e) {
                Logger.error("TCP-CLIENT", "Failed sending clipboard data to " + targetIp, e);
            }
        }, "ClipboardSenderThread").start();
    }

    public static void sendFile(String targetIp, File file) {
        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(targetIp, TCP_PORT), 5000);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                out.writeUTF("FILE");
                out.writeUTF(file.getName());
                out.writeLong(file.length());
                out.flush();

                String status = in.readUTF();
                if ("REJECTED".equals(status)) {
                    Logger.log("TCP-CLIENT", "Transfer rejected by remote peer " + targetIp + " for file: " + file.getName());
                    return;
                }

                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
                fis.close();
                Logger.log("TCP-CLIENT", String.format("Successfully sent file '%s' to %s", file.getName(), targetIp));
            } catch (Exception e) {
                Logger.error("TCP-CLIENT", "Failed sending file '" + file.getName() + "' to " + targetIp, e);
            }
        }, "FileSenderThread").start();
    }
}