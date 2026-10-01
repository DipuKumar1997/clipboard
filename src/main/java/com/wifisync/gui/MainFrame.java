package com.wifisync.gui;

import com.wifisync.model.Peer;
import com.wifisync.network.*;
import com.wifisync.service.ClipboardService;
import com.wifisync.util.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainFrame extends JFrame implements DiscoveryService.PeerDiscoveryListener, NetworkServer.MessageHandler {
    private JToggleButton clipboardSyncBtn;
    private JToggleButton fileSyncBtn;
    private JTable peerTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JButton sendFileBtn;

    private final List<Peer> peers = new ArrayList<>();
    private final DiscoveryService discoveryService;
    private final NetworkServer networkServer;
    private final ClipboardService clipboardService;
    private TrayIcon trayIcon;

    public MainFrame() {
        setTitle("Wi-Fi Sync Application");
        setSize(880, 580);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        initSystemTray();

        Logger.setGuiLogConsumer(msg -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });

        discoveryService = new DiscoveryService(this);
        networkServer = new NetworkServer(this);
        clipboardService = new ClipboardService();

        networkServer.start();
        discoveryService.startListener();

        toggleClipboardSync(true);

        Logger.log("GUI", "Application initialized with Clipboard Sync ACTIVE.");
        discoveryService.sendBroadcastDiscovery();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (SystemTray.isSupported()) {
                    setVisible(false);
                } else {
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    private void initUI() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        topPanel.setBorder(BorderFactory.createTitledBorder("Control Panel"));

        clipboardSyncBtn = new JToggleButton("Clipboard Sync: OFF");
        clipboardSyncBtn.addActionListener(e -> toggleClipboardSync(clipboardSyncBtn.isSelected()));

        fileSyncBtn = new JToggleButton("File Sync: OFF");
        fileSyncBtn.addActionListener(e -> toggleFileSync());

        JButton refreshBtn = new JButton("Scan Network");
        refreshBtn.addActionListener(e -> {
            peers.clear();
            tableModel.setRowCount(0);
            discoveryService.sendBroadcastDiscovery();
        });

        JButton toggleAllBtn = new JButton("Select / Unselect All");
        toggleAllBtn.addActionListener(e -> toggleAllPeers());

        sendFileBtn = new JButton("Send Files to Selected");
        sendFileBtn.addActionListener(e -> selectAndSendFiles());

        topPanel.add(clipboardSyncBtn);
        topPanel.add(fileSyncBtn);
        topPanel.add(refreshBtn);
        topPanel.add(toggleAllBtn);
        topPanel.add(sendFileBtn);

        String[] columns = {"Group Sync Enabled", "Host Name", "IP Address", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        peerTable = new JTable(tableModel);
        peerTable.setRowHeight(25);
        JScrollPane tableScrollPane = new JScrollPane(peerTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Active Network Peers in Group"));

        logArea = new JTextArea(12, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Application Logs"));

        setLayout(new BorderLayout(10, 10));
        add(topPanel, BorderLayout.NORTH);
        add(tableScrollPane, BorderLayout.CENTER);
        add(logScrollPane, BorderLayout.SOUTH);
    }

    private void initSystemTray() {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray tray = SystemTray.getSystemTray();

            int iconSize = 16;
            Image canvas = new java.awt.image.BufferedImage(iconSize, iconSize, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = (Graphics2D) canvas.getGraphics();
            g2.setColor(new Color(0, 120, 215));
            g2.fillOval(0, 0, iconSize, iconSize);
            g2.dispose();

            PopupMenu popup = new PopupMenu();
            MenuItem showItem = new MenuItem("Open Wi-Fi Sync");
            showItem.addActionListener(e -> {
                setVisible(true);
                setState(Frame.NORMAL);
            });

            MenuItem exitItem = new MenuItem("Quit App");
            exitItem.addActionListener(e -> {
                dispose();
                System.exit(0);
            });

            popup.add(showItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon = new TrayIcon(canvas, "Wi-Fi Sync (Active)", popup);
            trayIcon.addActionListener(e -> {
                setVisible(true);
                setState(Frame.NORMAL);
            });
            tray.add(trayIcon);
        } catch (Exception e) {
            Logger.error("TRAY", "Failed to load System Tray icon", e);
        }
    }

    private void toggleAllPeers() {
        if (tableModel.getRowCount() == 0) return;
        Boolean firstVal = (Boolean) tableModel.getValueAt(0, 0);
        boolean newVal = !Boolean.TRUE.equals(firstVal);
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(newVal, i, 0);
        }
    }

    private void toggleClipboardSync(boolean enable) {
        clipboardSyncBtn.setSelected(enable);
        if (enable) {
            clipboardSyncBtn.setText("Clipboard Sync: ON");
            clipboardSyncBtn.setBackground(new Color(144, 238, 144));
            clipboardService.startListening(
                text -> broadcastClipboardToGroup(text),
                files -> broadcastCopiedFilesToGroup(files)
            );
            Logger.log("MAIN", "Clipboard Sync Active: Listening for text & copied files (Ctrl+C).");
        } else {
            clipboardSyncBtn.setText("Clipboard Sync: OFF");
            clipboardSyncBtn.setBackground(null);
            clipboardService.stopListening();
            Logger.log("MAIN", "Clipboard Sync Paused.");
        }
    }

    private void toggleFileSync() {
        boolean active = fileSyncBtn.isSelected();
        fileSyncBtn.setText(active ? "File Sync: ON" : "File Sync: OFF");
        fileSyncBtn.setBackground(active ? new Color(144, 238, 144) : null);
    }

    private void broadcastClipboardToGroup(String text) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                NetworkClient.sendClipboardText((String) tableModel.getValueAt(i, 2), text);
            }
        }
    }

    private void broadcastCopiedFilesToGroup(List<File> files) {
        for (File file : files) {
            if (file.isFile()) {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                        NetworkClient.sendFile((String) tableModel.getValueAt(i, 2), file);
                    }
                }
            }
        }
    }

    private void selectAndSendFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = chooser.getSelectedFiles();
            for (File file : selectedFiles) {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                        NetworkClient.sendFile((String) tableModel.getValueAt(i, 2), file);
                    }
                }
            }
        }
    }

    @Override
    public synchronized void onPeerDiscovered(Peer peer) {
        if (!peers.contains(peer)) {
            peers.add(peer);
            tableModel.addRow(new Object[]{true, peer.getHostName(), peer.getIpAddress(), "Connected"});
            Logger.log("MAIN", ">>> NEW PEER CONNECTED: " + peer.getHostName() + " (" + peer.getIpAddress() + ")");
        }
    }

    @Override
    public void onClipboardReceived(String text, String fromIp) {
        if (clipboardSyncBtn.isSelected()) {
            clipboardService.setClipboardTextContent(text);
        }
    }

    @Override
    public boolean onFilePermissionRequested(String fileName, long fileSize, String fromIp) {
        double sizeInMb = fileSize / (1024.0 * 1024.0);
        
        AtomicBoolean approved = new AtomicBoolean(false);
        try {
            SwingUtilities.invokeAndWait(() -> {
                String message = String.format("Incoming File Transfer Request:\n\nFile Name: %s\nSize: %.2f MB\nSender IP: %s\n\nDo you want to receive this file?", 
                        fileName, sizeInMb, fromIp);
                
                int option = JOptionPane.showConfirmDialog(
                        this,
                        message,
                        "File Transfer Confirmation",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                
                approved.set(option == JOptionPane.YES_OPTION);
            });
        } catch (Exception e) {
            Logger.error("GUI", "Error showing file prompt dialog", e);
            return false;
        }
        return approved.get();
    }

    @Override
    public void onFileReceived(String fileName, byte[] data, String fromIp) {
        try {
            File downloadsDir = new File(System.getProperty("user.home"), "Downloads");
            File dest = new File(downloadsDir, "Sync_" + fileName);
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(data);
            }
            Logger.log("MAIN", ">>> FILE RECEIVED & SAVED: " + dest.getAbsolutePath());
        } catch (Exception e) {
            Logger.error("MAIN", "Failed saving incoming file from " + fromIp, e);
        }
    }

    @Override
    public void dispose() {
        discoveryService.stop();
        networkServer.stop();
        clipboardService.stopListening();
        super.dispose();
    }
}