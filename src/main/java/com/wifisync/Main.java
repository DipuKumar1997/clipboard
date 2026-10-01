package com.wifisync;

import com.wifisync.gui.MainFrame;
import com.wifisync.util.Logger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        boolean startMinimized = args.length > 0 && "--minimized".equals(args[0]);

        Logger.log("SYS", "Starting Wi-Fi Sync Application (Minimized: " + startMinimized + ")...");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            if (!startMinimized) {
                frame.setVisible(true);
            }
        });
    }
}