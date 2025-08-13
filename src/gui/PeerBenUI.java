package gui;

import javax.swing.*;
import clients.PeerBen;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class PeerBenUI extends JFrame {

    private PeerBen peer;
    private JTextArea logArea;
    private DefaultListModel<String> peerListModel;
    private DefaultListModel<String> fileListModel;
    private JLabel statusLabel;

    public PeerBenUI() {
        setTitle("Peer Ben Interface");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLookAndFeel();

        peer = new PeerBen();

        initMenu();
        initComponents();

        // Lancement du peer dans un thread séparé pour ne pas bloquer l'UI
        new Thread(() -> {
            try {
                peer.demarrer();
                log("Peer Ben started on port 8002");
                refreshAll();
            } catch (Exception e) {
                log("Erreur démarrage peer: " + e.getMessage());
            }
        }).start();

        // Timer pour rafraîchir automatiquement chaque 10 secondes
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshAll();
            }
        }, 10000, 10000);
    }

    private void setLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initMenu() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("Fichier");
        JMenuItem exitItem = new JMenuItem("Quitter");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);

        JMenu helpMenu = new JMenu("Aide");
        JMenuItem aboutItem = new JMenuItem("À propos");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "PeerBen P2P Application\nVersion 1.0\nAuteur: Ben",
                "À propos", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    private void initComponents() {
        // --- Peers List ---
        peerListModel = new DefaultListModel<>();
        JList<String> peerList = new JList<>(peerListModel);
        JScrollPane peerScroll = new JScrollPane(peerList);
        peerScroll.setBorder(BorderFactory.createTitledBorder("Connected Peers"));

        // --- Files List ---
        fileListModel = new DefaultListModel<>();
        JList<String> fileList = new JList<>(fileListModel);
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setBorder(BorderFactory.createTitledBorder("Available Files"));

        // Double-clic pour télécharger le fichier
        fileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    downloadFile(fileList);
                }
            }
        });

        // --- Log Area ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Logs"));

        // --- Status Bar ---
        statusLabel = new JLabel("Status: Ready");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());

        // --- Buttons ---
        JButton connectButton = createButton("Connect", e -> connectPeer());
        JButton searchButton = createButton("Search File", e -> searchFile());
        JButton downloadButton = createButton("Download File", e -> downloadFile(fileList));
        JButton refreshButton = createButton("Refresh", e -> refreshAll());

        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        buttonPanel.add(connectButton);
        buttonPanel.add(searchButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(refreshButton);

        // --- Main Layout ---
        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, peerScroll, fileScroll);
        leftSplit.setDividerLocation(250);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, buttonPanel);
        mainSplit.setDividerLocation(600);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainSplit, BorderLayout.CENTER);
        getContentPane().add(logScroll, BorderLayout.SOUTH);
        getContentPane().add(statusLabel, BorderLayout.NORTH);
    }

    private JButton createButton(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.addActionListener(action);
        button.setToolTipText(text + " action");
        button.setFocusPainted(false);
        button.setBackground(Color.LIGHT_GRAY);
        button.setBorder(BorderFactory.createEtchedBorder());
        return button;
    }

    private void downloadFile(JList<String> fileList) {
        String selected = fileList.getSelectedValue();
        if (selected != null) {
            String filename = selected.split(" \\(")[0];
            if (peer.telechargerFichier(filename)) {
                log("Download successful: " + filename);
                refreshFileList();
            } else {
                log("Download failed: " + filename);
            }
        } else {
            log("No file selected for download.");
        }
    }

    private void connectPeer() {
        String addr = JOptionPane.showInputDialog(this, "Peer Address:Port");
        if (addr != null && addr.contains(":")) {
            try {
                String[] parts = addr.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);
                peer.ajouterPeer(new entities.PeerInfo(ip, port, ""));
                log("Connected to " + ip + ":" + port);
                refreshPeerList();
            } catch (Exception ex) {
                log("Connection error: " + ex.getMessage());
            }
        }
    }

    private void searchFile() {
        String filename = JOptionPane.showInputDialog(this, "File name to search");
        if (filename != null && !filename.isEmpty()) {
            var results = peer.rechercherFichier(filename);
            if (results.isEmpty()) {
                log("File not found: " + filename);
            } else {
                log("File found at: " + results);
            }
        }
    }

    private void refreshAll() {
        refreshPeerList();
        refreshFileList();
        log("Lists refreshed.");
    }

    private void refreshPeerList() {
        SwingUtilities.invokeLater(() -> {
            peerListModel.clear();
            for (var p : peer.getPeersConnus()) {
                peerListModel.addElement(p.toString());
            }
            statusLabel.setText("Status: Peer list updated.");
        });
    }

    private void refreshFileList() {
        SwingUtilities.invokeLater(() -> {
            fileListModel.clear();
            File[] files = peer.getDossierPartage().listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        fileListModel.addElement(f.getName() + " (" + f.length() + " bytes)");
                    }
                }
            }
            statusLabel.setText("Status: File list updated.");
        });
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength()); // scroll automatique
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PeerBenUI ui = new PeerBenUI();
            ui.setVisible(true);
        });
    }
}
