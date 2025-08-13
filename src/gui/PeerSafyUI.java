package gui;

import javax.swing.*;
import clients.PeerSafy;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;

public class PeerSafyUI extends JFrame {

    private PeerSafy peer;

    private JTextArea logArea;

    private DefaultListModel<String> peerListModel;      // Peers connus (pour info)
    private DefaultListModel<String> remotePeerListModel; // Peers distants à sélectionner
    private DefaultListModel<String> fileListModel;      // Fichiers du peer sélectionné

    private JList<String> remotePeerList;
    private JList<String> fileList;

    private JLabel statusLabel;

    public PeerSafyUI() {
        setTitle("Peer Safy Interface");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLookAndFeel();

        peer = new PeerSafy();

        initMenu();
        initComponents();

        // Démarrage du peer dans un thread séparé
        new Thread(() -> {
            try {
                peer.demarrer();
                log("Peer Safy started on port 8001");
                refreshPeerLists();
            } catch (Exception e) {
                log("Erreur démarrage peer: " + e.getMessage());
            }
        }).start();

        // Timer pour rafraîchir automatiquement
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshPeerLists();
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
                "PeerSafy P2P Application\nVersion 1.0\nAuteur: Safy",
                "À propos", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    private void initComponents() {
        // --- Peers connus ---
        peerListModel = new DefaultListModel<>();
        JList<String> peerList = new JList<>(peerListModel);
        JScrollPane peerScroll = new JScrollPane(peerList);
        peerScroll.setBorder(BorderFactory.createTitledBorder("Connected Peers"));

        // --- Peers distants (pour sélectionner celui dont on veut les fichiers) ---
        remotePeerListModel = new DefaultListModel<>();
        remotePeerList = new JList<>(remotePeerListModel);
        JScrollPane remotePeerScroll = new JScrollPane(remotePeerList);
        remotePeerScroll.setBorder(BorderFactory.createTitledBorder("Select Peer"));
        remotePeerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        remotePeerList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedPeer = remotePeerList.getSelectedValue();
                if (selectedPeer != null) {
                    String[] parts = selectedPeer.split(":");
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    updateRemoteFiles(ip, port);
                }
            }
        });

        // --- Files List ---
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setBorder(BorderFactory.createTitledBorder("Files on Selected Peer"));

        // Double-clic pour télécharger un fichier
        fileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    downloadSelectedFile();
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
        JButton connectButton = createButton("Connect Peer", e -> connectPeer());
        JButton searchButton = createButton("Search File", e -> searchFile());
        JButton downloadButton = createButton("Download File", e -> downloadSelectedFile());
        JButton refreshButton = createButton("Refresh", e -> refreshPeerLists());

        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        buttonPanel.add(connectButton);
        buttonPanel.add(searchButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(refreshButton);

        // --- Layout principal ---
        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, remotePeerScroll, fileScroll);
        leftSplit.setDividerLocation(250);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, peerScroll, leftSplit);
        centerSplit.setDividerLocation(250);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerSplit, buttonPanel);
        mainSplit.setDividerLocation(650);

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

    private void connectPeer() {
        String addr = JOptionPane.showInputDialog(this, "Peer Address:Port");
        if (addr != null && addr.contains(":")) {
            try {
                String[] parts = addr.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);
                peer.ajouterPeer(new entities.PeerInfo(ip, port, ""));
                log("Connected to " + ip + ":" + port);
                refreshPeerLists();
            } catch (Exception ex) {
                log("Connection error: " + ex.getMessage());
            }
        }
    }

    private void searchFile() {
        String filename = JOptionPane.showInputDialog(this, "File name to search");
        if (filename != null && !filename.isEmpty()) {
            List<String> results = peer.rechercherFichier(filename);
            if (results.isEmpty()) {
                log("File not found: " + filename);
            } else {
                log("File found at: " + results);
            }
        }
    }

    private void refreshPeerLists() {
        SwingUtilities.invokeLater(() -> {
            // Peers connus
            peerListModel.clear();
            for (var p : peer.getPeersConnus()) {
                peerListModel.addElement(p.toString());
            }

            // Peers distants sélectionnables
            remotePeerListModel.clear();
            for (var p : peer.getPeersConnus()) {
                remotePeerListModel.addElement(p.getAdresse() + ":" + p.getPort());
            }

            statusLabel.setText("Status: Peer lists updated.");
        });
    }

    private void updateRemoteFiles(String ip, int port) {
        SwingUtilities.invokeLater(() -> {
            fileListModel.clear();
            List<String> files = peer.listerFichiersPeerDistant(ip, port);
            for (String f : files) {
                fileListModel.addElement(f);
            }
            statusLabel.setText("Files from " + ip + ":" + port + " loaded.");
        });
    }

    private void downloadSelectedFile() {
        String selectedFile = fileList.getSelectedValue();
        String selectedPeer = remotePeerList.getSelectedValue();
        if (selectedFile != null && selectedPeer != null) {
            String filename = selectedFile.split(" \\(")[0];
            String[] parts = selectedPeer.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);
            if (peer.telechargerFichierDepuisPeer(filename, ip, port)) {
                log("Download successful: " + filename + " from " + ip + ":" + port);
                refreshPeerLists();
            } else {
                log("Download failed: " + filename);
            }
        } else {
            log("Select a peer and a file to download.");
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PeerSafyUI ui = new PeerSafyUI();
            ui.setVisible(true);
        });
    }
}
