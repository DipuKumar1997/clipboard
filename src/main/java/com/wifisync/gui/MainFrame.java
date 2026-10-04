package com.wifisync.gui;

import com.wifisync.model.Peer;
import com.wifisync.network.*;
import com.wifisync.service.ClipboardService;
import com.wifisync.util.FolderZipper;
import com.wifisync.util.Logger;
import com.wifisync.util.ResourceMonitor;
import com.wifisync.util.SettingsManager;

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
    private JLabel ramLabel;

    private final List<Peer> peers = new ArrayList<>();
    private final DiscoveryService discoveryService;
    private final NetworkServer networkServer;
    private final ClipboardService clipboardService;
    private final ClipboardSyncManager clipboardSyncManager;
    private final SettingsManager settingsManager;
    private final ResourceMonitor resourceMonitor;
    private TrayIcon trayIcon;

    public MainFrame() {
        setTitle("Wi-Fi Sync Application");
        setSize(880, 610);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        settingsManager = new SettingsManager();

        initUI();
        initSystemTray();

        Logger.setGuiLogConsumer(msg -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });

        discoveryService = new DiscoveryService(this);
        networkServer = new NetworkServer(this);
        clipboardService = new ClipboardService();
        clipboardSyncManager = new ClipboardSyncManager(this::onClipboardDelivered);
        resourceMonitor = new ResourceMonitor();

        networkServer.start();
        clipboardSyncManager.start();
        discoveryService.startListener();

        resourceMonitor.start(3, usedBytes -> SwingUtilities.invokeLater(() -> {
            long mb = usedBytes / (1024 * 1024);
            ramLabel.setText("RAM: " + mb + " MB");
        }));

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

        JButton settingsBtn = new JButton("Settings");
        settingsBtn.addActionListener(e -> openSettingsDialog());

        topPanel.add(clipboardSyncBtn);
        topPanel.add(fileSyncBtn);
        topPanel.add(refreshBtn);
        topPanel.add(toggleAllBtn);
        topPanel.add(sendFileBtn);
        topPanel.add(settingsBtn);

        ramLabel = new JLabel("RAM: -- MB");
        ramLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        topPanel.add(ramLabel);

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

    private void openSettingsDialog() {
        JDialog dialog = new JDialog(this, "Settings", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setSize(480, 140);
        dialog.setLocationRelativeTo(this);

        JPanel form = new JPanel(new BorderLayout(8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel label = new JLabel("Download folder:");
        JTextField pathField = new JTextField(settingsManager.getDownloadDir());
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(pathField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel fieldRow = new JPanel(new BorderLayout(6, 0));
        fieldRow.add(pathField, BorderLayout.CENTER);
        fieldRow.add(browseBtn, BorderLayout.EAST);

        form.add(label, BorderLayout.NORTH);
        form.add(fieldRow, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> {
            File chosen = new File(pathField.getText());
            if (!chosen.exists() || !chosen.isDirectory()) {
                JOptionPane.showMessageDialog(dialog, "That folder doesn't exist.", "Invalid Folder", JOptionPane.WARNING_MESSAGE);
                return;
            }
            settingsManager.setDownloadDir(chosen.getAbsolutePath());
            dialog.dispose();
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        buttonRow.add(cancelBtn);
        buttonRow.add(saveBtn);

        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttonRow, BorderLayout.SOUTH);
        dialog.setVisible(true);
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
        List<String> targetIps = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                targetIps.add((String) tableModel.getValueAt(i, 2));
            }
        }
        if (!targetIps.isEmpty()) {
            clipboardSyncManager.sendToPeers(text, targetIps);
        }
    }

    /** Called once a clipboard update has been accepted (post-dedup) from either transport. */
    private void onClipboardDelivered(String text, String transport) {
        if (clipboardSyncBtn.isSelected()) {
            clipboardService.setClipboardTextContent(text);
        }
    }

    private void broadcastCopiedFilesToGroup(List<File> files) {
        for (File file : files) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                    NetworkClient.sendFile((String) tableModel.getValueAt(i, 2), file);
                }
            }
        }
    }

    private void selectAndSendFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
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
        // Route through the sync manager so TCP and speculative-UDP copies
        // of the same update are deduplicated in one place.
        clipboardSyncManager.handleTcpReceived(text, fromIp);
    }

    @Override
    public boolean onFilePermissionRequested(String fileName, long fileSize, String fromIp) {
        double sizeInMb = fileSize / (1024.0 * 1024.0);
        boolean isFolder = FolderZipper.isFolderTransferName(fileName);
        String displayName = isFolder ? FolderZipper.stripFolderMarker(fileName) : fileName;

        AtomicBoolean approved = new AtomicBoolean(false);
        try {
            SwingUtilities.invokeAndWait(() -> {
                String message = String.format(
                        "Incoming %s Transfer Request:\n\n%s Name: %s\nSize: %.2f MB\nSender IP: %s\n\nDo you want to receive this %s?",
                        isFolder ? "Folder" : "File", isFolder ? "Folder" : "File", displayName, sizeInMb, fromIp,
                        isFolder ? "folder" : "file");

                int option = JOptionPane.showConfirmDialog(
                        this,
                        message,
                        (isFolder ? "Folder" : "File") + " Transfer Confirmation",
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
            File downloadsDir = new File(settingsManager.getDownloadDir());

            if (FolderZipper.isFolderTransferName(fileName)) {
                String folderName = FolderZipper.stripFolderMarker(fileName);
                File destDir = new File(downloadsDir, "Sync_" + folderName);
                FolderZipper.unzipToDirectory(data, destDir);
                Logger.log("MAIN", ">>> FOLDER RECEIVED & EXTRACTED: " + destDir.getAbsolutePath());
                return;
            }

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
        clipboardSyncManager.stop();
        resourceMonitor.stop();
        super.dispose();
    }
}
