package com.wifisync.util;

import javax.swing.SwingUtilities;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class Logger {
    private static Consumer<String> guiLogConsumer;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static void setGuiLogConsumer(Consumer<String> consumer) {
        guiLogConsumer = consumer;
    }

    public static void log(String tag, String message) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String formattedMsg = String.format("[%s] [%s] %s", timestamp, tag, message);
        System.out.println(formattedMsg);

        if (guiLogConsumer != null) {
            SwingUtilities.invokeLater(() -> guiLogConsumer.accept(formattedMsg));
        }
    }

    public static void error(String tag, String message, Throwable t) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String formattedMsg = String.format("[%s] [ERROR] [%s] %s %s", 
            timestamp, tag, message, (t != null ? "(" + t.getMessage() + ")" : ""));

        System.err.println(formattedMsg);
        if (t != null) {
            t.printStackTrace();
        }

        if (guiLogConsumer != null) {
            SwingUtilities.invokeLater(() -> guiLogConsumer.accept(formattedMsg));
        }
    }
}