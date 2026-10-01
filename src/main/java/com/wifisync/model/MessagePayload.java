package com.wifisync.model;

import java.util.UUID;

public class MessagePayload {
    public String messageId;
    public String senderId;
    public String type; // TEXT, ACK, FILE
    public long timestamp;
    public String payload;

    public MessagePayload(String senderId, String type, String payload) {
        this.messageId = senderId + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.senderId = senderId;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.payload = payload;
    }

    public MessagePayload(String messageId, String senderId, String type, long timestamp, String payload) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.type = type;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    public String serialize() {
        String safePayload = payload == null ? "" : payload.replace("\n", "\\n").replace("\r", "\\r");
        return messageId + "|||" + senderId + "|||" + type + "|||" + timestamp + "|||" + safePayload;
    }

    public static MessagePayload deserialize(String data) {
        try {
            String[] parts = data.split("\\|\\|\\|", 5);
            if (parts.length < 5) return null;
            String unescaped = parts[4].replace("\\n", "\n").replace("\\r", "\r");
            return new MessagePayload(parts[0], parts[1], parts[2], Long.parseLong(parts[3]), unescaped);
        } catch (Exception e) {
            return null;
        }
    }
}