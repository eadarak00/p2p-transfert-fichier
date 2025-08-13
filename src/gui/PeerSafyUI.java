package gui;

import javax.swing.*;
import clients.PeerSafy;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;

public class PeerSafyUI extends JFrame {

    private PeerSafy peer;

    private JTextArea logArea;

    private DefaultListModel<String> peerListModel; // Peers connus (pour info)
    private DefaultListModel<String> remotePeerListModel; // Peers distants à sélectionner
    private DefaultListModel<String> fileListModel; // Fichiers du peer sélectionné
    private DefaultListModel<String> localFileListModel; // Fichiers partagés localement
    private JList<String> localFileList;

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

    // private void initComponents() {
    // // --- Peers connus ---
    // peerListModel = new DefaultListModel<>();
    // JList<String> peerList = new JList<>(peerListModel);
    // JScrollPane peerScroll = new JScrollPane(peerList);
    // peerScroll.setBorder(BorderFactory.createTitledBorder("Connected Peers"));

    // // --- Peers distants (pour sélectionner celui dont on veut les fichiers) ---
    // remotePeerListModel = new DefaultListModel<>();
    // remotePeerList = new JList<>(remotePeerListModel);
    // JScrollPane remotePeerScroll = new JScrollPane(remotePeerList);
    // remotePeerScroll.setBorder(BorderFactory.createTitledBorder("Select Peer"));
    // remotePeerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    // remotePeerList.addListSelectionListener(e -> {
    // if (!e.getValueIsAdjusting()) {
    // String selectedPeer = remotePeerList.getSelectedValue();
    // if (selectedPeer != null) {
    // String[] parts = selectedPeer.split(":");
    // String ip = parts[0];
    // int port = Integer.parseInt(parts[1]);
    // updateRemoteFiles(ip, port);
    // }
    // }
    // });

    // // --- Files List ---
    // fileListModel = new DefaultListModel<>();
    // fileList = new JList<>(fileListModel);
    // JScrollPane fileScroll = new JScrollPane(fileList);
    // fileScroll.setBorder(BorderFactory.createTitledBorder("Files on Selected
    // Peer"));

    // // Double-clic pour télécharger un fichier
    // fileList.addMouseListener(new MouseAdapter() {
    // public void mouseClicked(MouseEvent e) {
    // if (e.getClickCount() == 2) {
    // downloadSelectedFile();
    // }
    // }
    // });

    // // --- Log Area ---
    // logArea = new JTextArea();
    // logArea.setEditable(false);
    // JScrollPane logScroll = new JScrollPane(logArea);
    // logScroll.setBorder(BorderFactory.createTitledBorder("Logs"));

    // // --- Status Bar ---
    // statusLabel = new JLabel("Status: Ready");
    // statusLabel.setBorder(BorderFactory.createEtchedBorder());

    // // --- Buttons ---
    // JButton connectButton = createButton("Connect Peer", e -> connectPeer());
    // JButton searchButton = createButton("Search File", e -> searchFile());
    // JButton downloadButton = createButton("Download File", e ->
    // downloadSelectedFile());
    // JButton refreshButton = createButton("Refresh", e -> refreshPeerLists());

    // JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 5, 5));
    // buttonPanel.add(connectButton);
    // buttonPanel.add(searchButton);
    // buttonPanel.add(downloadButton);
    // buttonPanel.add(refreshButton);

    // // --- Layout principal ---
    // JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
    // remotePeerScroll, fileScroll);
    // leftSplit.setDividerLocation(250);

    // JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
    // peerScroll, leftSplit);
    // centerSplit.setDividerLocation(250);

    // JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
    // centerSplit, buttonPanel);
    // mainSplit.setDividerLocation(650);

    // getContentPane().setLayout(new BorderLayout());
    // getContentPane().add(mainSplit, BorderLayout.CENTER);
    // getContentPane().add(logScroll, BorderLayout.SOUTH);
    // getContentPane().add(statusLabel, BorderLayout.NORTH);
    // }

    private void initComponents() {
        // Création des modèles de données
        peerListModel = new DefaultListModel<>();
        remotePeerListModel = new DefaultListModel<>();
        fileListModel = new DefaultListModel<>();
        localFileListModel = new DefaultListModel<>();

        // --- Panel des fichiers locaux ---
        JList<String> localFileList = new JList<>(localFileListModel);
        localFileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane localFileScroll = new JScrollPane(localFileList);
        localFileScroll.setBorder(BorderFactory.createTitledBorder("Mes fichiers partagés"));

        // Double-clic pour ouvrir un fichier local
        localFileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openLocalFile();
                }
            }
        });

        // --- Panel des peers connus ---
        JList<String> peerList = new JList<>(peerListModel);
        JScrollPane peerScroll = new JScrollPane(peerList);
        peerScroll.setBorder(BorderFactory.createTitledBorder("Peers connectés"));

        // --- Panel des peers distants ---
        remotePeerList = new JList<>(remotePeerListModel);
        remotePeerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane remotePeerScroll = new JScrollPane(remotePeerList);
        remotePeerScroll.setBorder(BorderFactory.createTitledBorder("Sélectionner un peer"));

        // Sélection d'un peer pour voir ses fichiers
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

        // --- Panel des fichiers distants ---
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setBorder(BorderFactory.createTitledBorder("Fichiers du peer sélectionné"));

        // Double-clic pour télécharger un fichier
        fileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    downloadSelectedFile();
                }
            }
        });

        // --- Zone de logs ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Journal d'activité"));

        // --- Barre de statut ---
        statusLabel = new JLabel("Statut: Prêt");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());

        // --- Panel des boutons ---
        JButton connectButton = createButton("Connecter un peer", e -> connectPeer());
        JButton searchButton = createButton("Rechercher un fichier", e -> searchFile());
        JButton downloadButton = createButton("Télécharger", e -> downloadSelectedFile());
        JButton refreshButton = createButton("Actualiser", e -> refreshAll());
        JButton addFileButton = createButton("Ajouter fichier", e -> addFileToShare());
        JButton removeFileButton = createButton("Retirer fichier", e -> removeFileFromShare());

        JPanel buttonPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        buttonPanel.add(connectButton);
        buttonPanel.add(searchButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(addFileButton);
        buttonPanel.add(removeFileButton);
        buttonPanel.add(refreshButton);

        // --- Organisation du layout principal ---
        // Colonne de gauche (fichiers locaux et peers)
        JSplitPane leftColumn = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                localFileScroll,
                peerScroll);
        leftColumn.setDividerLocation(300);

        // Colonne centrale (peers distants et fichiers)
        JSplitPane centerColumn = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                remotePeerScroll,
                fileScroll);
        centerColumn.setDividerLocation(200);

        // Panneau principal (colonnes gauche et centre)
        JSplitPane mainPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftColumn,
                centerColumn);
        mainPanel.setDividerLocation(350);

        // Panneau complet avec logs en bas
        JSplitPane fullPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                mainPanel,
                logScroll);
        fullPanel.setDividerLocation(450);

        // Ajout des composants à la fenêtre
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(fullPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.EAST);
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

    private void refreshLocalFiles() {
        SwingUtilities.invokeLater(() -> {
            localFileListModel.clear();
            File dossier = peer.getDossierPartage();
            File[] fichiers = dossier.listFiles();

            if (fichiers != null) {
                for (File f : fichiers) {
                    if (f.isFile()) {
                        localFileListModel.addElement(f.getName() + " (" + formatFileSize(f.length()) + ")");
                    }
                }
            }
        });
    }

    private String formatFileSize(long size) {
        if (size < 1024)
            return size + " B";
        if (size < 1024 * 1024)
            return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024)
            return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    private void openLocalFile() {
        String selectedFile = localFileListModel.getElementAt(localFileList.getSelectedIndex()).split(" ")[0];
        File file = new File(peer.getDossierPartage(), selectedFile);

        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file);
            } catch (Exception e) {
                log("Error opening file: " + e.getMessage());
            }
        } else {
            log("Desktop operations not supported");
        }
    }

    private void refreshAll() {
        refreshPeerLists();
        refreshLocalFiles();
        log("Toutes les listes ont été actualisées");
    }

    private void removeFileFromShare() {
        String selected = ((JList<String>) localFileList).getSelectedValue();
        if (selected != null) {
            String filename = selected.split(" \\(")[0];
            int response = JOptionPane.showConfirmDialog(this,
                    "Voulez-vous vraiment retirer '" + filename + "' du partage?",
                    "Confirmation",
                    JOptionPane.YES_NO_OPTION);

            if (response == JOptionPane.YES_OPTION) {
                File file = new File(peer.getDossierPartage(), filename);
                if (file.delete()) {
                    log("Fichier retiré du partage: " + filename);
                    refreshLocalFiles();

                    // Notifier les peers de la suppression
                    peer.mettreAJourCacheComplet();
                } else {
                    log("Échec de la suppression du fichier: " + filename);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "Sélectionnez un fichier à retirer",
                    "Information",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void addFileToShare() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Sélectionner un fichier à partager");

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File source = fileChooser.getSelectedFile();
            File dest = new File(peer.getDossierPartage(), source.getName());

            try {
                Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log("Fichier ajouté au partage: " + source.getName());
                refreshLocalFiles();

                // Notifier les peers du nouveau fichier
                peer.mettreAJourCacheComplet();
            } catch (IOException e) {
                log("Erreur lors de l'ajout du fichier: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PeerSafyUI ui = new PeerSafyUI();
            ui.setVisible(true);
        });
    }

}
