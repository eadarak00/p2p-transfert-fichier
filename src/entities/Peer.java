package entities;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Classe Peer refactorisée avec architecture moderne et gestion d'erreurs
 * robuste
 */
public class Peer {
    private final String pseudo;
    private final int portEcoute;
    private final File dossierPartage;
    private final FileManager fileManager;
    private final Object fileLock = new Object();
    // Gestion des connexions réseau
    private final List<PeerInfo> peersConnus = new CopyOnWriteArrayList<>();
    private final Map<String, List<Metadata>> cacheFichiersPeers = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private volatile boolean actif = false;

    // Pool de threads pour la gestion des tâches
    private final ExecutorService executorPrincipal = Executors.newCachedThreadPool();
    private final ScheduledExecutorService schedulerMaintenance = Executors.newScheduledThreadPool(3);

    // Configuration
    private static final long PEER_TIMEOUT_MS = 3000;
    private static final int SOCKET_TIMEOUT_MS = 5000;
    private static final int BUFFER_SIZE = 8192;
    // private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final boolean DEBUG_MODE = false;

    public Peer(String pseudo, int portEcoute, String dossierPartage) {
        this.pseudo = validateString(pseudo, "Pseudo");
        this.portEcoute = validatePort(portEcoute);
        this.dossierPartage = new File(dossierPartage);

        if (!this.dossierPartage.exists()) {
            this.dossierPartage.mkdirs();
        }

        this.fileManager = new FileManager(this.dossierPartage.getPath());
    }

    /**
     * Démarre le peer avec tous ses services
     */
    public CompletableFuture<Void> demarrer() {
        return CompletableFuture.runAsync(() -> {
            try {
                serverSocket = new ServerSocket(portEcoute);
                actif = true;

                // Démarrer le serveur d'écoute
                executorPrincipal.submit(this::ecouterConnexions);

                // Programmer les tâches de maintenance
                programmerTachesMaintenance();

                logInfo("Peer '" + pseudo + "' démarré sur le port " + portEcoute);

                // Découverte initiale différée
                schedulerMaintenance.schedule(this::decouvriePeers, 1, TimeUnit.SECONDS);

            } catch (IOException e) {
                logError("Erreur lors du démarrage du peer", e);
                throw new RuntimeException(e);
            }
        }, executorPrincipal);
    }

    /**
     * Arrête proprement le peer et tous ses services
     */
    public void arreter() {
        logInfo("Arrêt du peer '" + pseudo + "'...");

        actif = false;

        // Arrêter les services dans l'ordre
        shutdownExecutor(schedulerMaintenance, "Scheduler de maintenance", 2);
        shutdownExecutor(executorPrincipal, "Executor principal", 5);

        // Fermer le socket serveur
        closeResource(serverSocket, "Socket serveur");

        logInfo("Peer '" + pseudo + "' arrêté");
    }

    /**
     * Programme les tâches de maintenance périodiques
     */
    private void programmerTachesMaintenance() {
        // Synchronisation des peers (toutes les 10 secondes)
        schedulerMaintenance.scheduleAtFixedRate(
                this::synchroniserPeersSilencieux, 10, 10, TimeUnit.SECONDS);

        // Nettoyage des peers inactifs (toutes les 15 secondes)
        schedulerMaintenance.scheduleAtFixedRate(
                this::nettoyerPeersInactifs, 15, 15, TimeUnit.SECONDS);

        // Mise à jour du cache des fichiers (toutes les 10 secondes)
        schedulerMaintenance.scheduleAtFixedRate(
                this::mettreAJourCacheComplet, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * Écoute les connexions entrantes
     */
    private void ecouterConnexions() {
        logInfo("Serveur d'écoute démarré");

        while (actif && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executorPrincipal.submit(() -> traiterRequetePeer(clientSocket));
            } catch (IOException e) {
                if (actif) {
                    logError("Erreur lors de l'acceptation d'une connexion", e);
                }
            }
        }
    }

    /**
     * Traite une requête d'un peer distant
     */
    private void traiterRequetePeer(Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS);

            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    InputStream socketIn = clientSocket.getInputStream();
                    OutputStream socketOut = clientSocket.getOutputStream()) {

                String commande = in.readLine();
                if (commande == null)
                    return;

                logDebug("Requête reçue: " + commande);

                String[] parts = commande.split(" ", 4);
                String cmd = parts[0].toUpperCase();

                switch (cmd) {
                    case "PING":
                        handlePing(out);
                        break;
                    case "LIST":
                        handleListFiles(socketOut);
                        break;
                    case "GET":
                        handleGetFile(parts, socketOut, out);
                        break;
                    case "PEERS":
                        handleGetPeers(socketOut);
                        break;
                    case "ANNOUNCE":
                        handleAnnounce(parts, clientSocket, out);
                        break;
                    case "UPLOAD": // NOUVEAU CAS
                        handleUploadFile(parts, socketIn, out);
                        break;
                    default:
                        out.println("ERREUR: commande inconnue");
                }
            }

        } catch (Exception e) {
            logError("Erreur lors du traitement d'une requête", e);
        }
    }

    // Handlers pour les différentes commandes
    private void handlePing(PrintWriter out) {
        out.println("PONG " + pseudo + " " + portEcoute);
    }

    private void handleListFiles(OutputStream socketOut) {
        synchronized (fileLock) {
            try {
                List<Metadata> metadatas = collecterMetadatasFichiers();
                byte[] data = serialiserListeMetadata(metadatas);
                envoyerDonneesBinaires(socketOut, data);
            } catch (Exception e) {
                logError("Erreur lors de l'envoi de la liste des fichiers", e);
                envoyerDonneesBinaires(socketOut, new byte[0]);
            }
        }
    }

    private void handleGetFile(String[] parts, OutputStream socketOut, PrintWriter out) {
        if (parts.length < 2) {
            out.println("ERREUR: commande GET invalide");
            return;
        }

        String nomFichier = parts[1];
        long offset = parts.length >= 3 ? parseOffset(parts[2]) : 0;

        envoyerFichier(nomFichier, socketOut, out, offset);
    }

    private void handleGetPeers(OutputStream socketOut) {
        try {
            List<PeerInfo> peersActifs = filtrerPeersActifs();
            byte[] data = PeerInfo.serialiserListe(peersActifs);
            envoyerDonneesBinaires(socketOut, data);
        } catch (Exception e) {
            logError("Erreur lors de l'envoi de la liste des peers", e);
            envoyerDonneesBinaires(socketOut, new byte[0]);
        }
    }

    private void handleAnnounce(String[] parts, Socket clientSocket, PrintWriter out) {
        if (parts.length < 3) {
            out.println("ERREUR: commande ANNOUNCE invalide");
            return;
        }

        try {
            String pseudoAnnonce = parts[1];
            int portAnnonce = Integer.parseInt(parts[2]);
            String adresseAnnonce = clientSocket.getInetAddress().getHostAddress();

            PeerInfo nouveauPeer = new PeerInfo(adresseAnnonce, portAnnonce, pseudoAnnonce);
            if (ajouterPeerSilencieux(nouveauPeer)) {
                out.println("OK PEER_ADDED");
            } else {
                out.println("OK PEER_UPDATED");
            }
        } catch (Exception e) {
            logError("Erreur lors de l'annonce", e);
            out.println("ERREUR: données invalides");
        }
    }

    /**
     * Découverte automatique des peers sur le réseau local
     */
    private void decouvriePeers() {
        logInfo("Démarrage de la découverte de peers...");

        CompletableFuture.runAsync(() -> {
            int peersInitiaux = peersConnus.size();
            List<CompletableFuture<Void>> taches = new ArrayList<>();

            // Scanner les ports de manière asynchrone
            for (int port = 8000; port <= 8100; port++) {
                if (port == portEcoute)
                    continue;

                final int finalPort = port;
                CompletableFuture<Void> tache = CompletableFuture.runAsync(() -> {
                    if (testerConnexionPeer("localhost", finalPort)) {
                        ajouterPeerSilencieux(new PeerInfo("localhost", finalPort, ""));
                    }
                }, executorPrincipal);

                taches.add(tache);
            }

            // Attendre toutes les tâches avec timeout
            CompletableFuture<Void> toutesLesTaches = CompletableFuture.allOf(
                    taches.toArray(new CompletableFuture[0]));

            try {
                toutesLesTaches.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                logError("Timeout lors de la découverte", e);
            }

            int nouveauxPeers = peersConnus.size() - peersInitiaux;
            if (nouveauxPeers > 0) {
                logInfo("Découverte terminée: " + nouveauxPeers + " nouveau(x) peer(s)");
            }
        }, executorPrincipal);
    }

    /**
     * Synchronisation silencieuse avec les peers connus
     */
    private void synchroniserPeersSilencieux() {
        if (!actif)
            return;

        Set<PeerInfo> nouveauxPeers = Collections.synchronizedSet(new HashSet<>());
        List<CompletableFuture<Void>> tachesSynchronisation = new ArrayList<>();

        for (PeerInfo peer : new ArrayList<>(peersConnus)) {
            CompletableFuture<Void> tache = CompletableFuture.runAsync(() -> {
                try {
                    annoncerAuPeer(peer);
                    Set<PeerInfo> peersDuPeer = recupererPeersDuPeer(peer);
                    nouveauxPeers.addAll(peersDuPeer);
                } catch (Exception e) {
                    logDebug("Erreur de sync avec " + peer + ": " + e.getMessage());
                }
            }, executorPrincipal);

            tachesSynchronisation.add(tache);
        }

        // Attendre toutes les synchronisations avec timeout
        CompletableFuture.allOf(tachesSynchronisation.toArray(new CompletableFuture[0]))
                .orTimeout(15, TimeUnit.SECONDS)
                .whenComplete((result, throwable) -> {
                    // Ajouter les nouveaux peers découverts
                    for (PeerInfo nouveauPeer : nouveauxPeers) {
                        if (!estPeerLocal(nouveauPeer)) {
                            ajouterPeerSilencieux(nouveauPeer);
                        }
                    }
                });
    }

    /**
     * Nettoyage des peers inactifs
     */
    private void nettoyerPeersInactifs() {
        if (!actif)
            return;

        List<PeerInfo> peersASupprimer = peersConnus.stream()
                .filter(peer -> !peer.estActif(PEER_TIMEOUT_MS))
                .filter(peer -> !testerConnexionPeer(peer.getAdresse(), peer.getPort()))
                .collect(Collectors.toList());

        if (!peersASupprimer.isEmpty()) {
            peersConnus.removeAll(peersASupprimer);

            // Nettoyer le cache
            peersASupprimer.forEach(peer -> {
                String cle = peer.getAdresse() + ":" + peer.getPort();
                cacheFichiersPeers.remove(cle);
            });

            logDebug("Nettoyé " + peersASupprimer.size() + " peer(s) inactif(s)");
        }
    }

    /**
     * Mise à jour complète du cache des fichiers
     */
    public void mettreAJourCacheComplet() {
        if (!actif)
            return;

        List<CompletableFuture<Void>> tachesMiseAJour = peersConnus.stream()
                .filter(peer -> peer.estActif(PEER_TIMEOUT_MS))
                .map(peer -> CompletableFuture.runAsync(() -> mettreAJourCachePeer(peer), executorPrincipal))
                .collect(Collectors.toList());

        // Exécuter toutes les mises à jour en parallèle avec timeout
        CompletableFuture.allOf(tachesMiseAJour.toArray(new CompletableFuture[0]))
                .orTimeout(20, TimeUnit.SECONDS)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logDebug("Timeout lors de la mise à jour du cache");
                    }
                });
    }

    /**
     * Test de connexion à un peer
     */
    private boolean testerConnexionPeer(String adresse, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(adresse, port), 2000);
            socket.setSoTimeout(2000);

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println("PING");
                String reponse = in.readLine();
                return reponse != null && reponse.startsWith("PONG");
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Annonce ce peer à un peer distant
     */
    private void annoncerAuPeer(PeerInfo peer) throws IOException {
        try (Socket socket = new Socket(peer.getAdresse(), peer.getPort())) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println("ANNOUNCE " + pseudo + " " + portEcoute);
                String reponse = in.readLine();

                if (reponse != null && reponse.startsWith("OK")) {
                    peer.updatePing();
                }
            }
        }
    }

    /**
     * Récupère la liste des peers connus par un peer distant
     */
    private Set<PeerInfo> recupererPeersDuPeer(PeerInfo peer) {
        Set<PeerInfo> peersDistants = new HashSet<>();

        try (Socket socket = new Socket(peer.getAdresse(), peer.getPort())) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            try (OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream()) {

                // Envoyer commande PEERS
                out.write("PEERS\n".getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Lire réponse binaire
                byte[] data = lireDonneesBinaires(in);
                if (data.length > 0) {
                    List<PeerInfo> peers = PeerInfo.deserialiserListe(data);
                    peersDistants.addAll(peers);
                }
            }
        } catch (Exception e) {
            logDebug("Erreur lors de la récupération des peers de " + peer + ": " + e.getMessage());
        }

        return peersDistants;
    }

    /**
     * Met à jour le cache des fichiers d'un peer
     */
    private void mettreAJourCachePeer(PeerInfo peer) {
        try (Socket socket = new Socket(peer.getAdresse(), peer.getPort())) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            try (OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream()) {

                // Envoyer commande LIST
                out.write("LIST\n".getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Lire réponse binaire
                byte[] data = lireDonneesBinaires(in);
                if (data.length > 0) {
                    List<Metadata> fichiers = deserialiserListeMetadata(data);
                    cacheFichiersPeers.put(peer.getAdresse() + ":" + peer.getPort(), fichiers);
                    peer.updatePing();
                }
            }
        } catch (Exception e) {
            logDebug("Erreur lors de la mise à jour du cache pour " + peer + ": " + e.getMessage());
        }
    }

    // ==================== MÉTHODES PUBLIQUES ====================

    /**
     * Télécharge un fichier depuis un peer spécifique
     */
    public boolean telechargerDepuisPeer(PeerInfo peer, String nomFichier) {
        File fichierLocal = new File(dossierPartage, nomFichier);
        if (fichierLocal.exists()) {
            String nomLocal = genererNomUnique(fichierLocal);
            logInfo("Fichier existant, sauvegarde sous: " + nomLocal);
            fichierLocal = new File(dossierPartage, nomLocal);
        }

        try (Socket socket = new Socket(peer.getAdresse(), peer.getPort())) {
            socket.setSoTimeout(30000);

            try (InputStream socketIn = socket.getInputStream();
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                out.println("GET " + nomFichier + " 0");

                String checksumServeur = lireLigne(socketIn);
                if (checksumServeur == null || checksumServeur.startsWith("ERREUR")) {
                    logError("Erreur lors de la demande du fichier: " + checksumServeur);
                    return false;
                }

                String tailleStr = lireLigne(socketIn);
                if (tailleStr == null)
                    return false;

                long tailleFichier = Long.parseLong(tailleStr);

                // Télécharger le fichier
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fichierLocal))) {
                    copierAvecProgression(socketIn, bos, tailleFichier, nomFichier);
                }

                // Vérifier l'intégrité
                if (verifierIntegriteFichier(fichierLocal, checksumServeur)) {
                    logInfo("Fichier téléchargé avec succès: " + fichierLocal.getName());
                    return true;
                } else {
                    logError("Erreur checksum pour " + nomFichier);
                    fichierLocal.delete();
                    return false;
                }
            }
        } catch (Exception e) {
            logError("Erreur lors du téléchargement depuis " + peer, e);
            if (fichierLocal.exists()) {
                fichierLocal.delete();
            }
            return false;
        }
    }

    /**
     * Ajoute manuellement un peer
     */
    public void ajouterPeer(PeerInfo peerInfo) {
        if (estPeerLocal(peerInfo)) {
            logInfo("Impossible de s'ajouter soi-même comme peer");
            return;
        }

        if (ajouterPeerSilencieux(peerInfo)) {
            logInfo("Nouveau peer ajouté: " + peerInfo);
        } else {
            logInfo("Peer mis à jour: " + peerInfo);
        }
    }

    /**
     * Synchronisation manuelle
     */
    public void synchroniserMaintenant() {
        logInfo("Synchronisation manuelle déclenchée...");

        CompletableFuture.runAsync(() -> {
            synchroniserPeersSilencieux();
            logInfo("Synchronisation terminée. Total: " + peersConnus.size() + " peers");
        }, executorPrincipal);
    }

    // ==================== MÉTHODES D'AFFICHAGE ====================

    public void afficherEtatReseau() {
        System.out.println("\n=== État du réseau P2P ===");
        System.out.println("Peer: " + pseudo + " (port " + portEcoute + ")");
        System.out.println("Peers connus: " + peersConnus.size());

        for (PeerInfo peer : peersConnus) {
            String clePeer = peer.getAdresse() + ":" + peer.getPort();
            List<Metadata> fichiers = cacheFichiersPeers.get(clePeer);
            int nbFichiers = fichiers != null ? fichiers.size() : 0;
            long inactiviteMs = System.currentTimeMillis() - peer.getDernierePing();
            String statut = peer.estActif(PEER_TIMEOUT_MS) ? "ACTIF" : "INACTIF";
            System.out.println("  - " + peer + " (" + nbFichiers + " fichiers) ["
                    + statut + " - " + (inactiviteMs / 1000) + "s]");
        }

        List<File> mesFichiers = fileManager.listerFichiers();
        System.out.println("Mes fichiers partagés: " + mesFichiers.size());
        for (File f : mesFichiers) {
            if (f.isFile()) {
                System.out.println("  - " + f.getName() + " (" + formatTaille(f.length()) + ")");
            }
        }
        System.out.println("========================\n");
    }

    public void afficherFichiersDisponibles() {
        System.out.println("\n=== Fichiers disponibles sur le réseau ===");

        Map<String, List<PeerInfo>> fichiersParNom = new HashMap<>();

        for (PeerInfo peer : peersConnus) {
            if (!peer.estActif(PEER_TIMEOUT_MS))
                continue;

            String clePeer = peer.getAdresse() + ":" + peer.getPort();
            List<Metadata> fichiers = cacheFichiersPeers.get(clePeer);

            if (fichiers != null) {
                for (Metadata meta : fichiers) {
                    fichiersParNom.computeIfAbsent(meta.getNom(), k -> new ArrayList<>()).add(peer);
                }
            }
        }

        if (fichiersParNom.isEmpty()) {
            System.out.println("Aucun fichier disponible sur le réseau");
        } else {
            fichiersParNom.forEach((nomFichier, peers) -> {
                System.out.print("  - " + nomFichier + " (disponible chez " + peers.size() + " peer(s): ");
                String pseudos = peers.stream()
                        .map(PeerInfo::getPseudo)
                        .collect(Collectors.joining(", "));
                System.out.println(pseudos + ")");
            });
        }
        System.out.println("==========================================\n");
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private boolean ajouterPeerSilencieux(PeerInfo peerInfo) {
        if (estPeerLocal(peerInfo))
            return false;

        Optional<PeerInfo> existant = peersConnus.stream()
                .filter(p -> p.equals(peerInfo))
                .findFirst();

        if (existant.isPresent()) {
            existant.get().updatePing();
            if (peerInfo.getPseudo() != null && !peerInfo.getPseudo().isEmpty()) {
                existant.get().setPseudo(peerInfo.getPseudo());
            }
            return false;
        } else {
            peersConnus.add(peerInfo);
            schedulerMaintenance.execute(() -> mettreAJourCachePeer(peerInfo));
            return true;
        }
    }

    private boolean estPeerLocal(PeerInfo peer) {
        return (peer.getAdresse().equals("localhost") || peer.getAdresse().equals("127.0.0.1"))
                && peer.getPort() == this.portEcoute;
    }

    private List<PeerInfo> filtrerPeersActifs() {
        return peersConnus.stream()
                .filter(peer -> peer.estActif(PEER_TIMEOUT_MS))
                .filter(peer -> !estPeerLocal(peer))
                .filter(peer -> peer.getPseudo() != null && !peer.getPseudo().trim().isEmpty())
                .collect(Collectors.toList());
    }

    private List<Metadata> collecterMetadatasFichiers() {
        return fileManager.listerFichiers().stream()
                .filter(File::isFile)
                .map(this::creerMetadata)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Metadata creerMetadata(File fichier) {
        try {
            return new Metadata(fichier.getName(), fichier.length(),
                    fileManager.calculerChecksum(fichier));
        } catch (Exception e) {
            logError("Erreur lors de la création des métadonnées pour " + fichier.getName(), e);
            return null;
        }
    }

    private void envoyerFichier(String nomFichier, OutputStream socketOut, PrintWriter out, long offset) {
        synchronized (fileLock) {
            try {
                File fichier = new File(dossierPartage, nomFichier);
                if (!fichier.exists() || !fichier.isFile()) {
                    out.println("ERREUR: fichier introuvable");
                    return;
                }

                String checksum = fileManager.calculerChecksum(fichier);
                long taille = fichier.length();

                out.println(checksum);
                out.println(taille);
                out.flush();

                try (RandomAccessFile raf = new RandomAccessFile(fichier, "r")) {
                    if (offset > 0) {
                        raf.seek(offset);
                    }

                    copierFichier(raf, socketOut, taille - offset);
                }

                logDebug("Fichier envoyé: " + nomFichier);

            } catch (Exception e) {
                logError("Erreur lors de l'envoi du fichier " + nomFichier, e);
                out.println("ERREUR lors de l'envoi du fichier");
            }
        }
    }

    private void copierFichier(RandomAccessFile source, OutputStream destination, long taille) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long reste = taille;
        int lu;

        while (reste > 0 && (lu = source.read(buffer, 0, (int) Math.min(buffer.length, reste))) != -1) {
            destination.write(buffer, 0, lu);
            reste -= lu;
        }
        destination.flush();
    }

    private void copierAvecProgression(InputStream source, OutputStream destination,
            long taille, String nomFichier) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long totalLu = 0;
        int lu;

        while (totalLu < taille && (lu = source.read(buffer, 0,
                (int) Math.min(buffer.length, taille - totalLu))) != -1) {
            destination.write(buffer, 0, lu);
            totalLu += lu;

            // Afficher progression pour gros fichiers
            if (taille > 1024 * 1024 && totalLu % (1024 * 1024) == 0) {
                int progression = (int) ((totalLu * 100) / taille);
                System.out.print("\rTéléchargement " + nomFichier + ": " + progression + "%");
            }
        }

        if (taille > 1024 * 1024) {
            System.out.println(); // Nouvelle ligne après progression
        }
    }

    private boolean verifierIntegriteFichier(File fichier, String checksumAttendu) {
        try {
            String checksumCalcule = fileManager.calculerChecksum(fichier);
            return checksumCalcule.equals(checksumAttendu);
        } catch (Exception e) {
            logError("Erreur lors de la vérification du checksum", e);
            return false;
        }
    }

    private String genererNomUnique(File fichier) {
        String nom = fichier.getName();
        int pointIndex = nom.lastIndexOf('.');
        String base = (pointIndex == -1) ? nom : nom.substring(0, pointIndex);
        String extension = (pointIndex == -1) ? "" : nom.substring(pointIndex);
        int compteur = 1;

        String nouveauNom;
        do {
            nouveauNom = base + "(" + compteur + ")" + extension;
            compteur++;
        } while (new File(dossierPartage, nouveauNom).exists());

        return nouveauNom;
    }

    // ==================== SÉRIALISATION ====================

    private byte[] serialiserListeMetadata(List<Metadata> metadatas) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            writeInt(bos, metadatas.size());

            for (Metadata meta : metadatas) {
                if (meta != null) {
                    byte[] metaData = meta.serialiser();
                    writeInt(bos, metaData.length);
                    bos.write(metaData);
                } else {
                    writeInt(bos, 0);
                }
            }

            return bos.toByteArray();
        } catch (IOException e) {
            logError("Erreur lors de la sérialisation des métadonnées", e);
            return new byte[0];
        }
    }

    private List<Metadata> deserialiserListeMetadata(byte[] data) {
        if (data == null || data.length < 4) {
            return new ArrayList<>();
        }

        List<Metadata> metadatas = new ArrayList<>();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
            int count = readInt(bis);
            if (count < 0 || count > 10000) {
                throw new IOException("Nombre de métadonnées invalide: " + count);
            }

            for (int i = 0; i < count; i++) {
                int metaDataLen = readInt(bis);

                if (metaDataLen == 0)
                    continue;

                if (metaDataLen < 0 || metaDataLen > 100000) {
                    throw new IOException("Longueur de métadonnées invalide: " + metaDataLen);
                }

                byte[] metaData = new byte[metaDataLen];
                if (bis.read(metaData) != metaDataLen) {
                    throw new IOException("Impossible de lire les métadonnées " + i);
                }

                try {
                    Metadata meta = Metadata.deserialiser(metaData);
                    metadatas.add(meta);
                } catch (IOException e) {
                    logDebug("Métadonnées " + i + " corrompues, ignorées: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logError("Erreur lors de la désérialisation des métadonnées", e);
        }

        return metadatas;
    }

    private void envoyerDonneesBinaires(OutputStream out, byte[] data) {
        try {
            ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
            lengthBuffer.putInt(data.length);
            out.write(lengthBuffer.array());
            out.write(data);
            out.flush();
        } catch (IOException e) {
            logError("Erreur lors de l'envoi de données binaires", e);
        }
    }

    private byte[] lireDonneesBinaires(InputStream in) throws IOException {
        byte[] lengthBytes = new byte[4];
        if (in.read(lengthBytes) != 4) {
            throw new IOException("Impossible de lire la longueur");
        }

        int length = ByteBuffer.wrap(lengthBytes).getInt();
        if (length < 0 || length > 10_000_000) { // 10MB max
            throw new IOException("Longueur de données invalide: " + length);
        }

        if (length == 0)
            return new byte[0];

        byte[] data = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(data, totalRead, length - totalRead);
            if (read == -1) {
                throw new IOException("Fin de flux inattendue");
            }
            totalRead += read;
        }

        return data;
    }

    private String lireLigne(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n')
                break;
            buffer.write(b);
        }
        return buffer.toString("UTF-8").trim();
    }

    // ==================== MÉTHODES UTILITAIRES DIVERSES ====================

    private static void writeInt(OutputStream os, int value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(value);
        os.write(buffer.array());
    }

    private static int readInt(InputStream is) throws IOException {
        byte[] buffer = new byte[4];
        if (is.read(buffer) != 4) {
            throw new IOException("Impossible de lire un entier");
        }
        return ByteBuffer.wrap(buffer).getInt();
    }

    private long parseOffset(String offsetStr) {
        try {
            return Long.parseLong(offsetStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatTaille(long octets) {
        if (octets < 1024)
            return octets + " B";
        if (octets < 1024 * 1024)
            return String.format("%.1f KB", octets / 1024.0);
        if (octets < 1024 * 1024 * 1024)
            return String.format("%.1f MB", octets / (1024.0 * 1024));
        return String.format("%.1f GB", octets / (1024.0 * 1024 * 1024));
    }

    // ==================== VALIDATION ====================

    private String validateString(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être vide");
        }
        return value.trim();
    }

    private int validatePort(int port) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port invalide: " + port);
        }
        return port;
    }

    // ==================== LOGGING ====================

    private void logInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    private void logError(String message, Exception e) {
        System.err.println("[ERROR] " + message + ": " + e.getMessage());
        if (DEBUG_MODE) {
            e.printStackTrace();
        }
    }

    private void logError(String message) {
        System.err.println("[ERROR] " + message);
    }

    private void logDebug(String message) {
        if (DEBUG_MODE) {
            System.out.println("[DEBUG] " + message);
        }
    }

    // ==================== GESTION DES RESSOURCES ====================

    private void shutdownExecutor(ExecutorService executor, String name, int timeoutSeconds) {
        if (executor != null && !executor.isShutdown()) {
            logDebug("Arrêt de " + name + "...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                    logDebug("Arrêt forcé de " + name);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void closeResource(AutoCloseable resource, String name) {
        if (resource != null) {
            try {
                resource.close();
                logDebug(name + " fermé");
            } catch (Exception e) {
                logError("Erreur lors de la fermeture de " + name, e);
            }
        }
    }

    // ==================== GETTERS ====================

    public String getPseudo() {
        return pseudo;
    }

    public int getPort() {
        return portEcoute;
    }

    public List<PeerInfo> getPeersConnus() {
        return new ArrayList<>(peersConnus);
    }

    public File getDossierPartage() {
        return dossierPartage;
    }

    public boolean estActif() {
        return actif;
    }

    /**
     * Obtient des statistiques sur le peer
     */
    public Map<String, Object> getStatistiques() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pseudo", pseudo);
        stats.put("port", portEcoute);
        stats.put("actif", actif);
        stats.put("peers_connus", peersConnus.size());
        stats.put("peers_actifs", peersConnus.stream()
                .mapToInt(p -> p.estActif(PEER_TIMEOUT_MS) ? 1 : 0).sum());
        stats.put("fichiers_partages", fileManager.listerFichiers().size());
        stats.put("cache_fichiers_peers", cacheFichiersPeers.size());
        return stats;
    }

    /**
     * Test de connectivité avec tous les peers
     */
    public void testerConnectivite() {
        System.out.println("\n=== Test de connectivité ===");

        if (peersConnus.isEmpty()) {
            System.out.println("Aucun peer connu pour tester la connectivité");
            return;
        }

        List<CompletableFuture<Boolean>> tests = peersConnus.stream()
                .map(peer -> CompletableFuture.supplyAsync(() -> {
                    System.out.print("Test de " + peer + "... ");
                    boolean connecte = testerConnexionPeer(peer.getAdresse(), peer.getPort());
                    System.out.println(connecte ? "OK" : "ECHEC");
                    if (connecte)
                        peer.updatePing();
                    return connecte;
                }, executorPrincipal))
                .collect(Collectors.toList());

        // Attendre tous les tests
        CompletableFuture.allOf(tests.toArray(new CompletableFuture[0]))
                .orTimeout(10, TimeUnit.SECONDS)
                .thenRun(() -> {
                    long peersActifs = tests.stream()
                            .mapToLong(future -> {
                                try {
                                    return future.get() ? 1 : 0;
                                } catch (Exception e) {
                                    return 0;
                                }
                            }).sum();
                    System.out.println("Résultat: " + peersActifs + "/" + peersConnus.size() + " peers actifs");
                    System.out.println("============================\n");
                })
                .exceptionally(throwable -> {
                    System.out.println("Timeout lors des tests de connectivité");
                    System.out.println("============================\n");
                    return null;
                });
    }

    /**
     * Télécharge un fichier depuis un peer spécifique
     * 
     * @param filename Nom du fichier à télécharger
     * @param ip       Adresse IP du peer distant
     * @param port     Port du peer distant
     * @return true si le téléchargement a réussi, false sinon
     */
    public boolean telechargerFichierDepuisPeer(String filename, String ip, int port) {
        // Créer un PeerInfo temporaire pour utiliser la méthode existante
        PeerInfo peer = new PeerInfo(ip, port, "");
        return telechargerDepuisPeer(peer, filename);
    }

    /**
     * Liste les noms des fichiers disponibles sur un peer distant
     * 
     * @param ip   Adresse IP du peer distant
     * @param port Port du peer distant
     * @return Liste des noms des fichiers disponibles ou liste vide en cas d'erreur
     */
    public List<String> listerFichiersPeerDistant(String ip, int port) {
        List<Metadata> metadatas = listerFichiersPeerDistantAvecMetadata(ip, port);
        if (metadatas == null) {
            return new ArrayList<>();
        }
        return metadatas.stream()
                .map(Metadata::getNom)
                .collect(Collectors.toList());
    }

    /**
     * Méthode interne pour obtenir les métadonnées complètes
     */
    private List<Metadata> listerFichiersPeerDistantAvecMetadata(String ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            try (OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream()) {

                out.write("LIST\n".getBytes(StandardCharsets.UTF_8));
                out.flush();

                byte[] data = lireDonneesBinaires(in);
                if (data.length > 0) {
                    return deserialiserListeMetadata(data);
                }
            }
        } catch (Exception e) {
            logError("Erreur lors de la récupération des fichiers du peer " + ip + ":" + port, e);
        }
        return null;
    }

    /**
     * Recherche un fichier sur le réseau
     * 
     * @param filename Nom du fichier à rechercher
     * @return Liste des identifiants des peers possédant le fichier (format
     *         "ip:port")
     */
    public List<String> rechercherFichier(String filename) {
        return rechercherFichierAvecPeerInfo(filename).stream()
                .map(peer -> peer.getAdresse() + ":" + peer.getPort())
                .collect(Collectors.toList());
    }

    /**
     * Méthode existante renommée pour garder la fonctionnalité originale
     */
    public List<PeerInfo> rechercherFichierAvecPeerInfo(String filename) {
        return peersConnus.stream()
                .filter(peer -> peer.estActif(PEER_TIMEOUT_MS))
                .filter(peer -> {
                    String clePeer = peer.getAdresse() + ":" + peer.getPort();
                    List<Metadata> fichiers = cacheFichiersPeers.get(clePeer);
                    return fichiers != null && fichiers.stream()
                            .anyMatch(meta -> meta.getNom().equals(filename));
                })
                .collect(Collectors.toList());
    }

    /**
     * Télécharge un fichier depuis le réseau P2P
     * 
     * @param nomFichier Le nom du fichier à télécharger
     * @return true si le téléchargement a réussi, false sinon
     */
    public boolean telechargerFichier(String nomFichier) {
        // Utilisation de la nouvelle méthode qui retourne List<String>
        List<String> peersAvecFichier = rechercherFichier(nomFichier);

        if (peersAvecFichier.isEmpty()) {
            logInfo("Fichier '" + nomFichier + "' introuvable sur le réseau");
            return false;
        }

        logInfo("Fichier trouvé chez " + peersAvecFichier.size() + " peer(s)");

        for (String peerId : peersAvecFichier) {
            // Découper l'identifiant "ip:port"
            String[] parts = peerId.split(":");
            if (parts.length != 2)
                continue;

            String ip = parts[0];
            int port;
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }

            logInfo("Tentative de téléchargement depuis " + peerId);
            if (telechargerFichierDepuisPeer(nomFichier, ip, port)) {
                mettreAJourCacheComplet();
                return true;
            }
        }

        logInfo("Échec du téléchargement depuis tous les peers");
        return false;
    }

    /**
     * Supprime un fichier du dossier de partage.
     *
     * @param filename Nom du fichier à supprimer
     * @return true si le fichier a été supprimé, false sinon
     */
    public boolean supprimerFichier(String filename) {
        if (filename == null || filename.isEmpty())
            return false;

        File file = new File(dossierPartage, filename);
        if (!file.exists() || !file.isFile())
            return false;

        boolean deleted = file.delete();
        if (deleted) {
            // Notifier les autres peers que le fichier a été supprimé
            mettreAJourCacheComplet();
        }
        return deleted;
    }

    /**
     * Lit le contenu d'un fichier du dossier de partage.
     *
     * @param filename Nom du fichier
     * @return Contenu du fichier sous forme de String
     * @throws IOException si le fichier n'existe pas ou lecture impossible
     */
    public String lireFichier(String filename) throws IOException {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Nom de fichier invalide");
        }

        File file = new File(dossierPartage, filename);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("Fichier introuvable : " + filename);
        }

        return new String(Files.readAllBytes(file.toPath()));
    }

    /**
     * Upload un fichier vers un peer distant
     * 
     * @param filename   Nom du fichier local à envoyer
     * @param targetIp   Adresse IP du peer destinataire
     * @param targetPort Port du peer destinataire
     * @return true si l'upload a réussi, false sinon
     */
    public boolean uploaderFichierVersPeer(String filename, String targetIp, int targetPort) {
        File fichierLocal = new File(dossierPartage, filename);

        if (!fichierLocal.exists() || !fichierLocal.isFile()) {
            logError("Fichier local introuvable: " + filename);
            return false;
        }

        try (Socket socket = new Socket(targetIp, targetPort)) {
            socket.setSoTimeout(30000);

            try (InputStream fileIn = new FileInputStream(fichierLocal);
                    OutputStream socketOut = socket.getOutputStream();
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Calculer le checksum du fichier
                String checksum = fileManager.calculerChecksum(fichierLocal);
                long taille = fichierLocal.length();

                // Envoyer la commande UPLOAD avec les métadonnées
                out.println("UPLOAD " + filename + " " + taille + " " + checksum);

                // Lire la réponse du serveur
                String reponse = in.readLine();
                if (reponse == null || !reponse.startsWith("READY")) {
                    logError("Peer distant pas prêt à recevoir: " + reponse);
                    return false;
                }

                // Envoyer le fichier
                logInfo("Début de l'upload de " + filename + " vers " + targetIp + ":" + targetPort);
                copierAvecProgressionUpload(fileIn, socketOut, taille, filename);

                // Attendre confirmation
                String confirmation = in.readLine();
                if ("SUCCESS".equals(confirmation)) {
                    logInfo("Upload réussi: " + filename);
                    return true;
                } else {
                    logError("Échec de l'upload: " + confirmation);
                    return false;
                }

            }
        } catch (Exception e) {
            logError("Erreur lors de l'upload vers " + targetIp + ":" + targetPort, e);
            return false;
        }
    }

    /**
     * Copie un fichier avec affichage de progression pour l'upload
     */
    private void copierAvecProgressionUpload(InputStream source, OutputStream destination,
            long taille, String nomFichier) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long totalEnvoye = 0;
        int lu;

        while (totalEnvoye < taille && (lu = source.read(buffer, 0,
                (int) Math.min(buffer.length, taille - totalEnvoye))) != -1) {
            destination.write(buffer, 0, lu);
            destination.flush();
            totalEnvoye += lu;

            // Afficher progression pour gros fichiers
            if (taille > 1024 * 1024 && totalEnvoye % (1024 * 1024) == 0) {
                int progression = (int) ((totalEnvoye * 100) / taille);
                System.out.print("\rUpload " + nomFichier + ": " + progression + "%");
            }
        }

        if (taille > 1024 * 1024) {
            System.out.println(); // Nouvelle ligne après progression
        }
    }

    /**
     * Gère la réception d'un fichier uploadé par un peer distant
     */
    private void handleUploadFile(String[] parts, InputStream socketIn, PrintWriter out) {
        if (parts.length < 4) {
            out.println("ERREUR: commande UPLOAD invalide (format: UPLOAD filename size checksum)");
            return;
        }

        String nomFichier = parts[1];
        long tailleFichier;
        String checksumAttendu = parts[3];

        try {
            tailleFichier = Long.parseLong(parts[2]);
            if (tailleFichier < 0 || tailleFichier > 1_000_000_000L) { // Max 1GB
                out.println("ERREUR: taille de fichier invalide");
                return;
            }
        } catch (NumberFormatException e) {
            out.println("ERREUR: taille de fichier invalide");
            return;
        }

        // Vérifier si le fichier existe déjà
        File fichierDestination = new File(dossierPartage, nomFichier);
        if (fichierDestination.exists()) {
            // Générer un nom unique
            String nomUnique = genererNomUnique(fichierDestination);
            fichierDestination = new File(dossierPartage, nomUnique);
            logInfo("Fichier existant, sauvegarde sous: " + nomUnique);
        }

        synchronized (fileLock) {
            try {
                out.println("READY");
                out.flush();

                // Recevoir le fichier
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fichierDestination))) {
                    copierAvecProgression(socketIn, bos, tailleFichier, nomFichier);
                }

                // Vérifier l'intégrité
                if (verifierIntegriteFichier(fichierDestination, checksumAttendu)) {
                    out.println("SUCCESS");
                    logInfo("Fichier reçu avec succès: " + fichierDestination.getName());

                    // Mettre à jour le cache
                    schedulerMaintenance.execute(this::mettreAJourCacheComplet);
                } else {
                    out.println("ERREUR: checksum invalide");
                    fichierDestination.delete();
                    logError("Checksum invalide pour le fichier reçu: " + nomFichier);
                }

            } catch (Exception e) {
                out.println("ERREUR: " + e.getMessage());
                if (fichierDestination.exists()) {
                    fichierDestination.delete();
                }
                logError("Erreur lors de la réception du fichier " + nomFichier, e);
            }
        }
    }


    public FileManager getFileManager(){
        return this.fileManager;
    }

}