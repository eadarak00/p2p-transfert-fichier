package gui;

import javax.swing.*;
import clients.PeerBen;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;
import javax.swing.border.Border;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

public class PeerBenUI extends JFrame {

    private PeerBen peer;
    private JTextArea logArea;
    private DefaultListModel<String> peerListModel;
    private DefaultListModel<String> remotePeerListModel;
    private DefaultListModel<String> fileListModel;
    private DefaultListModel<String> localFileListModel;
    private JList<String> localFileList;
    private JList<String> remotePeerList;
    private JList<String> fileList;
    private JLabel statusLabel;

    // Constantes pour le style
    private static final Font TITLE_FONT = new Font("Poppins", Font.BOLD, 28);
    private static final Font BUTTON_FONT = new Font("Poppins", Font.BOLD, 24);
    private static final Font LIST_FONT = new Font("Poppins", Font.PLAIN, 24);
    private static final Font STATUS_FONT = new Font("Poppins", Font.ITALIC, 26);
    private static final Font LOG_FONT = new Font("Consolas", Font.PLAIN, 22);

    private static final Color PRIMARY_COLOR = new Color(70, 130, 180);
    private static final Color SECONDARY_COLOR = new Color(100, 149, 237);
    private static final Color ACCENT_COLOR = new Color(255, 140, 0);
    private static final Color BACKGROUND_COLOR = new Color(248, 249, 250);
    private static final Color TEXT_COLOR = new Color(33, 37, 41);

    public PeerBenUI() {
        setTitle("PeerBen - Partage P2P Avancé");
        setSize(1400, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLookAndFeel();
        customizeUIDefaults();

        peer = new PeerBen();

        initMenu();
        initComponents();

        // Démarrage du peer dans un thread séparé
        new Thread(() -> {
            try {
                peer.demarrer();
                log("Peer Ben démarré avec succès sur le port 8002");
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
            // Configuration Nimbus avec personnalisations
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void customizeUIDefaults() {
        // Personnalisation globale des composants Nimbus
        UIManager.put("control", BACKGROUND_COLOR);
        UIManager.put("info", new Color(255, 255, 225));
        UIManager.put("nimbusBase", PRIMARY_COLOR);
        UIManager.put("nimbusAlertYellow", ACCENT_COLOR);
        UIManager.put("nimbusDisabledText", new Color(128, 128, 128));
        UIManager.put("nimbusFocus", SECONDARY_COLOR);
        UIManager.put("nimbusGreen", new Color(176, 179, 50));
        UIManager.put("nimbusInfoBlue", new Color(66, 139, 221));
        UIManager.put("nimbusLightBackground", BACKGROUND_COLOR);
        UIManager.put("nimbusOrange", ACCENT_COLOR);
        UIManager.put("nimbusRed", new Color(169, 46, 34));
        UIManager.put("nimbusSelectedText", Color.WHITE);
        UIManager.put("nimbusSelectionBackground", SECONDARY_COLOR);
        UIManager.put("text", TEXT_COLOR);
    }

    private void initMenu() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setFont(BUTTON_FONT);
        menuBar.setBackground(PRIMARY_COLOR);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, ACCENT_COLOR));

        JMenu fileMenu = createStyledMenu("Fichier");
        JMenuItem exitItem = createStyledMenuItem(" Quitter");
        exitItem.addActionListener(e -> {
            // Personnaliser les polices pour la confirmation de sortie
            UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.BOLD, 26));
            UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));

            String message = "<html><div style='text-align: center; padding: 30px;'>" +
                    "<h3 style='color: #FF8C00;'> Confirmation de sortie</h3>" +
                    "<p style='font-size: 20px; margin: 20px 0;'>Voulez-vous vraiment quitter<br/>" +
                    "<b style='color: #4682B4;'>PeerBen</b> ?</p>" +
                    "<p style='font-size: 16px; color: #666;'>Toutes les connexions actives seront fermées</p>" +
                    "</div></html>";

            int option = JOptionPane.showConfirmDialog(this, message,
                    "Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (option == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });
        fileMenu.add(exitItem);

        JMenu helpMenu = createStyledMenu("Aide");
        JMenuItem aboutItem = createStyledMenuItem("À propos");
        aboutItem.addActionListener(e -> {
            // Personnaliser les polices pour le dialog "À propos"
            UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.PLAIN, 24));
            UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));

            String message = "<html><div style='text-align: center; padding: 40px;'>" +
                    "<h1 style='color: #4682B4; font-size: 32px; margin-bottom: 20px;'>PeerBen P2P Application</h1>" +
                    "<table style='margin: 0 auto; font-size: 20px; line-height: 2.0;'>" +
                    "<tr><td style='font-weight: bold; color: #4682B4; padding: 5px 15px;'>Version:</td>" +
                    "<td style='padding: 5px 15px;'>2.0 Enhanced</td></tr>" +
                    "<tr><td style='font-weight: bold; color: #4682B4; padding: 5px 15px;'>Auteur:</td>" +
                    "<td style='padding: 5px 15px;'>Ben</td></tr>" +
                    "<tr><td style='font-weight: bold; color: #4682B4; padding: 5px 15px;'>Description:</td>" +
                    "<td style='padding: 5px 15px;'>Interface moderne pour le partage P2P</td></tr>" +
                    "</table>" +
                    "<p style='color: #FF8C00; font-size: 22px; font-style: italic; margin-top: 30px;'>Conçu avec passion</p>"
                    +
                    "</div></html>";

            JOptionPane.showMessageDialog(this, message, "À propos de PeerBen", JOptionPane.INFORMATION_MESSAGE);
        });
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    private JMenu createStyledMenu(String text) {
        JMenu menu = new JMenu(text);
        menu.setFont(BUTTON_FONT);
        menu.setForeground(Color.WHITE);
        return menu;
    }

    private JMenuItem createStyledMenuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(new Font("Poppins", Font.PLAIN, 22));
        return item;
    }

    private void initComponents() {
        // Création des modèles de données
        peerListModel = new DefaultListModel<>();
        remotePeerListModel = new DefaultListModel<>();
        fileListModel = new DefaultListModel<>();
        localFileListModel = new DefaultListModel<>();

        // --- Panel des fichiers locaux ---
        localFileList = createStyledList(localFileListModel);
        JScrollPane localFileScroll = createStyledScrollPane(localFileList, "Mes Fichiers Partagés");

        localFileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openLocalFile();
                }
            }
        });

        // --- Panel des peers connus ---
        JList<String> peerList = createStyledList(peerListModel);
        JScrollPane peerScroll = createStyledScrollPane(peerList, "Peers Connectés");

        // --- Panel des peers distants ---
        remotePeerList = createStyledList(remotePeerListModel);
        remotePeerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane remotePeerScroll = createStyledScrollPane(remotePeerList, "Sélectionner un Peer");

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
        fileList = createStyledList(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane fileScroll = createStyledScrollPane(fileList, "Fichiers Disponibles");

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
        logArea.setFont(LOG_FONT);
        logArea.setBackground(new Color(43, 43, 43));
        logArea.setForeground(new Color(248, 248, 242));
        logArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        JScrollPane logScroll = createStyledScrollPane(logArea, "📊 Journal d'Activité");

        // --- Barre de statut ---
        statusLabel = new JLabel("🟢 Statut: Prêt");
        statusLabel.setFont(STATUS_FONT);
        statusLabel.setForeground(PRIMARY_COLOR);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0, ACCENT_COLOR),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(BACKGROUND_COLOR);

        // --- Panel des boutons ---
        JPanel buttonPanel = createButtonPanel();

        // --- Organisation du layout principal ---
        JSplitPane leftColumn = new JSplitPane(JSplitPane.VERTICAL_SPLIT, localFileScroll, peerScroll);
        leftColumn.setDividerLocation(400);
        leftColumn.setDividerSize(8);
        leftColumn.setBorder(null);

        JSplitPane centerColumn = new JSplitPane(JSplitPane.VERTICAL_SPLIT, remotePeerScroll, fileScroll);
        centerColumn.setDividerLocation(250);
        centerColumn.setDividerSize(8);
        centerColumn.setBorder(null);

        JSplitPane mainPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftColumn, centerColumn);
        mainPanel.setDividerLocation(450);
        mainPanel.setDividerSize(10);
        mainPanel.setBorder(null);

        JSplitPane fullPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainPanel, logScroll);
        fullPanel.setDividerLocation(550);
        fullPanel.setDividerSize(10);
        fullPanel.setBorder(null);

        // Configuration du layout principal
        getContentPane().setLayout(new BorderLayout(10, 10));
        getContentPane().add(fullPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.EAST);
        getContentPane().add(statusLabel, BorderLayout.SOUTH);
        getContentPane().setBackground(BACKGROUND_COLOR);

        // Bordure pour la fenêtre principale
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private JList<String> createStyledList(DefaultListModel<String> model) {
        JList<String> list = new JList<>(model);
        list.setFont(LIST_FONT);
        list.setSelectionBackground(SECONDARY_COLOR);
        list.setSelectionForeground(Color.WHITE);
        list.setBackground(Color.WHITE);
        list.setForeground(TEXT_COLOR);
        list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        list.setFixedCellHeight(35);
        return list;
    }

    private JScrollPane createStyledScrollPane(JComponent component, String title) {
        JScrollPane scrollPane = new JScrollPane(component);

        // Bordure personnalisée avec titre
        Border titleBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(PRIMARY_COLOR, 2, true),
                title,
                0, 0, TITLE_FONT, PRIMARY_COLOR);

        Border paddingBorder = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(paddingBorder, titleBorder));

        // Style de la scrollbar
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(16, 0));
        scrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 16));

        return scrollPane;
    }

    // private JPanel createButtonPanel() {
    // JPanel buttonPanel = new JPanel(new GridBagLayout());
    // buttonPanel.setBackground(BACKGROUND_COLOR);
    // buttonPanel.setBorder(BorderFactory.createCompoundBorder(
    // BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_COLOR),
    // BorderFactory.createEmptyBorder(20, 20, 20, 20)
    // ));

    // GridBagConstraints gbc = new GridBagConstraints();
    // gbc.fill = GridBagConstraints.HORIZONTAL;
    // gbc.insets = new Insets(10, 0, 10, 0);
    // gbc.weightx = 1.0;

    // String[] buttonData = {
    // "Connecter Peer", "connectPeer",
    // "Rechercher", "searchFile",
    // "Télécharger", "downloadSelectedFile",
    // "Ajouter Fichier", "addFileToShare",
    // "Retirer Fichier", "removeFileFromShare",
    // "Lire Fichier", "openLocalFile",
    // "Actualiser", "refreshAll"
    // };

    // for (int i = 0; i < buttonData.length; i += 2) {
    // gbc.gridy = i / 2;
    // JButton button = createStyledButton(buttonData[i], buttonData[i + 1]);
    // buttonPanel.add(button, gbc);
    // }

    // return buttonPanel;
    // }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        buttonPanel.setBackground(BACKGROUND_COLOR);
        buttonPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_COLOR),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 0, 10, 0);
        gbc.weightx = 1.0;

        String[] buttonData = {
                "Connecter Peer", "connectPeer",
                "Rechercher", "searchFile",
                "Télécharger", "downloadSelectedFile",
                "Uploader Fichier", "uploadFileToSelectedPeer", // NOUVEAU BOUTON
                "Ajouter Fichier", "addFileToShare",
                "Retirer Fichier", "removeFileFromShare",
                "Lire Fichier", "openLocalFile",
                "Actualiser", "refreshAll"
        };

        for (int i = 0; i < buttonData.length; i += 2) {
            gbc.gridy = i / 2;
            JButton button = createStyledButton(buttonData[i], buttonData[i + 1]);
            buttonPanel.add(button, gbc);
        }

        return buttonPanel;
    }

    private JButton createStyledButton(String text, String action) {
        JButton button = new JButton(text);
        button.setFont(BUTTON_FONT);
        button.setPreferredSize(new Dimension(220, 50));
        button.setMinimumSize(new Dimension(220, 50));

        // Gradient background
        button.setBackground(PRIMARY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);

        // Bordure arrondie
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SECONDARY_COLOR, 2, true),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));

        // Effets hover
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(SECONDARY_COLOR);
                button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(PRIMARY_COLOR);
                button.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });

        // Action listeners
        switch (action) {
            case "connectPeer":
                button.addActionListener(e -> connectPeer());
                break;
            case "searchFile":
                button.addActionListener(e -> searchFile());
                break;
            case "downloadSelectedFile":
                button.addActionListener(e -> downloadSelectedFile());
                break;
            case "uploadFileToSelectedPeer": button.addActionListener(e -> uploadFileToSelectedPeer()); break;  // NOUVEAU

            case "addFileToShare":
                button.addActionListener(e -> addFileToShare());
                break;
            case "removeFileFromShare":
                button.addActionListener(e -> removeFileFromShare());
                break;
            case "openLocalFile":
                button.addActionListener(e -> openLocalFile());
                break;
            case "refreshAll":
                button.addActionListener(e -> refreshAll());
                break;
        }

        return button;
    }

    private void connectPeer() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel label = new JLabel("Adresse IP:Port du peer à connecter:", JLabel.LEFT);
        label.setFont(new Font("Poppins", Font.BOLD, 26));
        label.setForeground(PRIMARY_COLOR);
        panel.add(label);

        JTextField textField = new JTextField("127.0.0.1:8002");
        textField.setFont(new Font("Poppins", Font.PLAIN, 24));
        textField.setPreferredSize(new Dimension(300, 45));
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SECONDARY_COLOR, 2, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        panel.add(textField);

        // Personnaliser les boutons du dialog
        UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));
        UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.PLAIN, 26));

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Connexion à un Peer", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String addr = textField.getText();
            if (addr != null && addr.contains(":")) {
                try {
                    String[] parts = addr.split(":");
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    peer.ajouterPeer(new entities.PeerInfo(ip, port, ""));
                    log("Connecté avec succès à " + ip + ":" + port);
                    refreshPeerLists();
                } catch (Exception ex) {
                    log("Erreur de connexion: " + ex.getMessage());
                }
            }
        }
    }

    private void searchFile() {
        // Personnaliser les polices pour le dialog
        UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.BOLD, 26));
        UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));

        JPanel panel = new JPanel(new GridLayout(2, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel label = new JLabel("Nom du fichier à rechercher:", JLabel.LEFT);
        label.setFont(new Font("Poppins", Font.BOLD, 26));
        label.setForeground(PRIMARY_COLOR);
        panel.add(label);

        JTextField textField = new JTextField();
        textField.setFont(new Font("Poppins", Font.PLAIN, 24));
        textField.setPreferredSize(new Dimension(400, 45));
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SECONDARY_COLOR, 2, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        panel.add(textField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Recherche de Fichier", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String filename = textField.getText();
            if (filename != null && !filename.isEmpty()) {
                List<String> results = peer.rechercherFichier(filename);
                if (results.isEmpty()) {
                    log("Fichier non trouvé: " + filename);
                } else {
                    log("Fichier trouvé: " + results);
                }
            }
        }
    }

    private void refreshPeerLists() {
        SwingUtilities.invokeLater(() -> {
            peerListModel.clear();
            for (var p : peer.getPeersConnus()) {
                peerListModel.addElement("" + p.toString());
            }

            remotePeerListModel.clear();
            for (var p : peer.getPeersConnus()) {
                remotePeerListModel.addElement(p.getAdresse() + ":" + p.getPort());
            }

            statusLabel.setText("Statut: Listes des peers mises à jour");
        });
    }

    private void updateRemoteFiles(String ip, int port) {
        SwingUtilities.invokeLater(() -> {
            fileListModel.clear();
            List<String> files = peer.listerFichiersPeerDistant(ip, port);
            for (String f : files) {
                fileListModel.addElement("" + f);
            }
            statusLabel.setText("Fichiers de " + ip + ":" + port + " chargés");
        });
    }

    private void downloadSelectedFile() {
        String selectedFile = fileList.getSelectedValue();
        String selectedPeer = remotePeerList.getSelectedValue();

        if (selectedFile != null && selectedPeer != null) {
            String filename = selectedFile.replace("", "").split(" \\(")[0];
            String[] parts = selectedPeer.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            if (peer.telechargerFichierDepuisPeer(filename, ip, port)) {
                log("Téléchargement réussi: " + filename + " depuis " + ip + ":" + port);
                refreshLocalFiles();
            } else {
                log("Échec du téléchargement: " + filename);
            }
        } else {
            // Personnaliser les polices pour le message d'avertissement
            UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.PLAIN, 26));
            UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));

            JOptionPane.showMessageDialog(this,
                    "<html><div style='text-align: center; padding: 20px;'>" +
                            "<h3 style='color: #FF8C00;'>⚠️ Attention</h3>" +
                            "<p style='font-size: 24px; margin-top: 15px;'>Sélectionnez un peer et un fichier à télécharger</p>"
                            +
                            "</div></html>",
                    "Sélection requise", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
            logArea.append("[" + timestamp + "] " + message + "\n");
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
                        localFileListModel.addElement("" + f.getName() + " (" + formatFileSize(f.length()) + ")");
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

    private void refreshAll() {
        refreshPeerLists();
        refreshLocalFiles();
        log("Toutes les listes ont été actualisées");
    }

    private void removeFileFromShare() {
        String selected = localFileList.getSelectedValue();
        if (selected == null) {
            // Personnaliser les polices pour le message d'information
            UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.PLAIN, 26));
            UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));

            JOptionPane.showMessageDialog(this,
                    "<html><div style='text-align: center; padding: 20px;'>" +
                            "<h3 style='color: #4682B4;'>⚠️ Information</h3>" +
                            "<p style='font-size: 24px; margin-top: 15px;'>Veuillez sélectionner un fichier à retirer du partage</p>"
                            +
                            "</div></html>",
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String filename = selected.replace("", "").split(" \\(")[0];

        // Personnaliser les polices pour la confirmation
        UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.BOLD, 26));
        UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));

        String message = "<html><div style='text-align: center; padding: 20px;'>" +
                "<h3 style='color: #FF8C00;'>Confirmation de suppression</h3>" +
                "<p style='font-size: 20px; margin: 20px 0;'>Voulez-vous vraiment retirer<br/>" +
                "<b style='color: #4682B4;'>" + filename + "</b><br/>" +
                "du partage ?</p></div></html>";

        int response = JOptionPane.showConfirmDialog(this, message,
                "Confirmation de suppression", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (response == JOptionPane.YES_OPTION) {
            boolean deleted = peer.supprimerFichier(filename);
            if (deleted) {
                log("Fichier retiré du partage: " + filename);
                refreshLocalFiles();
            } else {
                log("Échec de la suppression: " + filename);
            }
        }
    }

    private void addFileToShare() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Sélectionner un fichier à partager");

        // Personnaliser la police du FileChooser
        setFileChooserFont(fileChooser.getComponents());

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File source = fileChooser.getSelectedFile();
            File dest = new File(peer.getDossierPartage(), source.getName());

            try {
                Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log("Fichier ajouté au partage: " + source.getName());
                refreshLocalFiles();
                peer.mettreAJourCacheComplet();
            } catch (IOException e) {
                log("Erreur lors de l'ajout: " + e.getMessage());
            }
        }
    }

    // Méthode utilitaire pour personnaliser les polices du FileChooser
    private void setFileChooserFont(Component[] components) {
        Font fileChooserFont = new Font("Poppins", Font.PLAIN, 20);
        for (Component component : components) {
            if (component instanceof Container) {
                setFileChooserFont(((Container) component).getComponents());
            }
            try {
                component.setFont(fileChooserFont);
            } catch (Exception ignored) {
                // Ignore les erreurs de définition de police
            }
        }
    }

    private void openLocalFile() {
        String selected = localFileList.getSelectedValue();
        if (selected == null) {
            // Personnaliser les polices pour le message d'information
            UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.PLAIN, 26));
            UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));

            JOptionPane.showMessageDialog(this,
                    "<html><div style='text-align: center; padding: 20px;'>" +
                            "<h3 style='color: #4682B4;'>Information</h3>" +
                            "<p style='font-size: 24px; margin-top: 15px;'>Veuillez sélectionner un fichier à ouvrir</p>"
                            +
                            "</div></html>",
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String filename = selected.replace("", "").split(" \\(")[0];

        try {
            String content = peer.lireFichier(filename);

            JTextArea textArea = new JTextArea(content);
            textArea.setEditable(false);
            textArea.setFont(new Font("Consolas", Font.PLAIN, 22));
            textArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SECONDARY_COLOR, 2, true),
                    BorderFactory.createEmptyBorder(20, 20, 20, 20)));
            textArea.setBackground(new Color(248, 249, 250));
            textArea.setForeground(TEXT_COLOR);

            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(900, 700));
            scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Personnaliser les polices pour le dialog de contenu
            UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.BOLD, 26));
            UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));

            JOptionPane.showMessageDialog(this, scrollPane,
                    "Contenu de " + filename, JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            log("Erreur lecture fichier: " + e.getMessage());
        }
    }

    private void uploadFileToSelectedPeer() {
    String selectedFile = localFileList.getSelectedValue();
    String selectedPeer = remotePeerList.getSelectedValue();
    
    if (selectedFile == null || selectedPeer == null) {
        UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.PLAIN, 26));
        UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));
        
        JOptionPane.showMessageDialog(this,
            "<html><div style='text-align: center; padding: 20px;'>" +
            "<h3 style='color: #FF8C00;'>⚠️ Attention</h3>" +
            "<p style='font-size: 24px; margin-top: 15px;'>Sélectionnez un fichier local et un peer de destination</p>" +
            "</div></html>",
            "Sélection requise", JOptionPane.WARNING_MESSAGE);
        return;
    }
    
    // Extraire le nom du fichier (sans la taille)
    String filename = selectedFile.replace("📄", "").split(" \\(")[0].trim();
    
    // Extraire IP et port du peer sélectionné
    String[] parts = selectedPeer.split(":");
    String ip = parts[0];
    int port = Integer.parseInt(parts[1]);
    
    // Confirmation avant upload
    UIManager.put("OptionPane.messageFont", new Font("Poppins", Font.BOLD, 26));
    UIManager.put("OptionPane.buttonFont", new Font("Poppins", Font.BOLD, 24));
    
    String message = "<html><div style='text-align: center; padding: 20px;'>" +
                    "<h3 style='color: #4682B4;'>Confirmation d'upload</h3>" +
                    "<p style='font-size: 20px; margin: 20px 0;'>Voulez-vous uploader<br/>" +
                    "<b style='color: #FF8C00;'>" + filename + "</b><br/>" +
                    "vers le peer<br/>" +
                    "<b style='color: #4682B4;'>" + selectedPeer + "</b> ?</p></div></html>";

    int response = JOptionPane.showConfirmDialog(this, message,
            "Confirmation d'upload", JOptionPane.YES_NO_OPTION, 
            JOptionPane.QUESTION_MESSAGE);

    if (response == JOptionPane.YES_OPTION) {
        // Exécuter l'upload dans un thread séparé pour ne pas bloquer l'interface
        SwingUtilities.invokeLater(() -> statusLabel.setText("🔄 Upload en cours..."));
        
        new Thread(() -> {
            boolean success = peer.uploaderFichierVersPeer(filename, ip, port);
            
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    log("Upload réussi: " + filename + " vers " + ip + ":" + port);
                    statusLabel.setText("✅ Upload terminé avec succès");
                } else {
                    log("Échec de l'upload: " + filename);
                    statusLabel.setText("❌ Échec de l'upload");
                }
                
                // Actualiser les listes après un délai
                Timer timer = new Timer(true);
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(() -> {
                            updateRemoteFiles(ip, port);
                            statusLabel.setText("🟢 Statut: Prêt");
                        });
                    }
                }, 2000);
            });
        }).start();
    }
}

    public static void main(String[] args) {
        // Configuration pour de meilleures performances graphiques
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                PeerBenUI ui = new PeerBenUI();
                ui.setVisible(true);
                ui.refreshLocalFiles();
                ui.log("Interface PeerBen chargée avec succès!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}