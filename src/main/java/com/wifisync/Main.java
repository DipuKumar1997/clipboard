package com.wifisync;

import com.wifisync.gui.MainFrame;
import com.wifisync.util.Logger;
import com.wifisync.util.SingleInstanceGuard;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        boolean startMinimized = args.length > 0 && "--minimized".equals(args[0]);

        if (!SingleInstanceGuard.tryAcquire()) {
            // Another copy of AirMesh is already running on this machine.
            // This is what caused duplicate tray icons on Linux when the
            // systemd service and a manual launch overlapped -- refuse to
            // start a second instance instead of quietly stacking listeners
            // and tray icons on top of each other.
            Logger.log("SYS", "Another AirMesh instance is already running. Exiting.");
            System.exit(0);
        }

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
