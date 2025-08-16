import clients.*;
import entities.Peer;
import entities.PeerInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MainTest {

    public static void main(String[] args) {
        System.out.println("🚀 Démarrage des tests P2P complets...\n");
        
        try {
            // --- 1. Initialisation et démarrage des peers ---
            testDemarragePeers();
            
            // Attendre que tous les peers se découvrent
            Thread.sleep(12000);
            
            // --- 2. Tests de découverte et connectivité ---
            testDecouvertePeers();
            
            // --- 3. Tests de téléchargement ---
            testTelechargementsBasiques();
            
            // --- 4. Tests de gestion d'erreurs ---
            testGestionErreurs();
            
            // --- 5. Tests de fonctionnalités avancées ---
            testFonctionnalitesAvancees();
            
            // --- 6. Tests de robustesse ---
            testRobustesse();
            
            // --- 7. Tests de performance ---
            testPerformance();
            
            System.out.println("\n🎉 Tous les tests sont terminés !");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors des tests : " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Nettoyage
            arreterTousLesPeers();
        }
    }
    
    // Variables globales pour les peers
    private static PeerSafy safy;
    private static PeerBen ben;
    private static PeerAimerou aimerou;
    private static PeerMoussa moussa;
    private static PeerFatou fatou;
    private static PeerDiallo diallo;
    
    private static void testDemarragePeers() throws Exception {
        System.out.println("=== 1. Test de démarrage des peers ===");
        
        // Créer les peers
        safy = new PeerSafy();
        ben = new PeerBen();
        aimerou = new PeerAimerou();
        moussa = new PeerMoussa();
        fatou = new PeerFatou();
        diallo = new PeerDiallo();
        
        // Démarrer tous les peers
        CompletableFuture<Void> demarrages = CompletableFuture.allOf(
            safy.demarrer(),
            ben.demarrer(),
            aimerou.demarrer(),
            moussa.demarrer(),
            fatou.demarrer(),
            diallo.demarrer()
        );
        
        demarrages.get(10, TimeUnit.SECONDS);
        resultatTest(true, "Tous les peers démarrés avec succès");
        
        // Vérifier l'état des peers
        resultatTest(safy.estActif(), "Safy est actif");
        resultatTest(ben.estActif(), "Ben est actif");
        resultatTest(aimerou.estActif(), "Aimerou est actif");
        resultatTest(moussa.estActif(), "Moussa est actif");
        resultatTest(fatou.estActif(), "Fatou est actif");
        resultatTest(diallo.estActif(), "Diallo est actif");
        
        // Afficher les fichiers initiaux
        afficherFichiersInitiaux();
        
        System.out.println();
    }
    
    private static void testDecouvertePeers() throws Exception {
        System.out.println("=== 2. Test de découverte des peers ===");
        
        // Test de synchronisation manuelle
        safy.synchroniserMaintenant();
        ben.synchroniserMaintenant();
        Thread.sleep(3000);
        
        // Vérifier que les peers se connaissent
        List<PeerInfo> peersSafy = safy.getPeersConnus();
        List<PeerInfo> peersBen = ben.getPeersConnus();
        
        resultatTest(peersSafy.size() > 0, "Safy a découvert " + peersSafy.size() + " peers");
        resultatTest(peersBen.size() > 0, "Ben a découvert " + peersBen.size() + " peers");
        
        // Test d'ajout manuel de peer
        PeerInfo peerManuel = new PeerInfo("localhost", 8003, "AimerouManuel");
        safy.ajouterPeer(peerManuel);
        Thread.sleep(1000);
        resultatTest(safy.getPeersConnus().size() >= peersSafy.size(), "Ajout manuel de peer réussi");
        
        // Afficher l'état du réseau
        safy.afficherEtatReseau();
        
        // Test de connectivité
        safy.testerConnectivite();
        
        System.out.println();
    }
    
    private static void testTelechargementsBasiques() throws Exception {
        System.out.println("=== 3. Test de téléchargements basiques ===");
        
        // Mettre à jour les caches
        safy.mettreAJourCacheComplet();
        ben.mettreAJourCacheComplet();
        Thread.sleep(3000);
        
        // Afficher les fichiers disponibles
        safy.afficherFichiersDisponibles();
        
        // Test 1: Téléchargement fichier existant depuis peer spécifique
        System.out.println("\n📥 Ben télécharge 'test1.txt' depuis Safy (localhost:8001)...");
        boolean download1 = ben.telechargerFichierDepuisPeer("test1.txt", "localhost", 8001);
        resultatTest(download1, "Téléchargement direct depuis peer spécifique");
        
        // Test 2: Téléchargement via recherche réseau
        System.out.println("\n📥 Aimerou télécharge 'document.txt' via recherche réseau...");
        boolean download2 = aimerou.telechargerFichier("document.txt");
        resultatTest(download2, "Téléchargement via recherche réseau");
        
        // Test 3: Lister fichiers d'un peer distant
        System.out.println("\n📋 Fatou liste les fichiers de Moussa (localhost:8004)...");
        List<String> fichiersMoussa = fatou.listerFichiersPeerDistant("localhost", 8004);
        resultatTest(fichiersMoussa != null && !fichiersMoussa.isEmpty(), 
                    "Liste des fichiers récupérée : " + fichiersMoussa.size() + " fichiers");
        fichiersMoussa.forEach(f -> System.out.println("  - " + f));
        
        // Test 4: Recherche de fichier sur le réseau
        System.out.println("\n🔍 Diallo recherche 'test1.txt' sur le réseau...");
        List<String> peersAvecTest1 = diallo.rechercherFichier("test1.txt");
        resultatTest(peersAvecTest1 != null && !peersAvecTest1.isEmpty(), 
                    "Fichier trouvé chez " + peersAvecTest1.size() + " peer(s)");
        peersAvecTest1.forEach(peer -> System.out.println("  - " + peer));
        
        System.out.println();
    }
    
    private static void testGestionErreurs() throws Exception {
        System.out.println("=== 4. Test de gestion d'erreurs ===");
        
        // Test 1: Téléchargement fichier inexistant
        System.out.println("\n📥 Essai de téléchargement d'un fichier inexistant...");
        boolean downloadInexistant = safy.telechargerFichierDepuisPeer("fichier_inexistant.txt", "localhost", 8002);
        resultatTest(!downloadInexistant, "Erreur gérée : fichier inexistant");
        
        // Test 2: Connexion à un peer inexistant
        System.out.println("\n📥 Essai de connexion à un peer inexistant (port 9999)...");
        List<String> fichiersInexistant = ben.listerFichiersPeerDistant("localhost", 9999);
        resultatTest(fichiersInexistant.isEmpty(), "Erreur gérée : peer inexistant");
        
        // Test 3: Recherche d'un fichier inexistant
        System.out.println("\n🔍 Recherche d'un fichier inexistant...");
        List<String> peersFichierInexistant = aimerou.rechercherFichier("fichier_qui_nexiste_pas.txt");
        resultatTest(peersFichierInexistant.isEmpty(), "Aucun peer trouvé pour fichier inexistant");
        
        // Test 4: Tentative de lecture d'un fichier inexistant
        System.out.println("\n📖 Tentative de lecture d'un fichier inexistant...");
        try {
            moussa.lireFichier("fichier_inexistant.txt");
            resultatTest(false, "Exception devrait être levée");
        } catch (IOException e) {
            resultatTest(true, "Exception IOException correctement levée");
        }
        
        System.out.println();
    }
    
    private static void testFonctionnalitesAvancees() throws Exception {
        System.out.println("=== 5. Test de fonctionnalités avancées ===");
        
        // Test 1: Création et lecture de fichier
        System.out.println("\n➕ Création d'un nouveau fichier par Safy...");
        File nouveauFichier = new File(safy.getDossierPartage(), "nouveau_fichier.txt");
        try (FileOutputStream fos = new FileOutputStream(nouveauFichier)) {
            fos.write("Contenu du nouveau fichier créé par Safy".getBytes());
        }
        resultatTest(nouveauFichier.exists(), "Nouveau fichier créé");
        
        // Lire le fichier
        String contenu = safy.lireFichier("nouveau_fichier.txt");
        resultatTest(contenu.contains("Safy"), "Contenu du fichier lu correctement");
        System.out.println("Contenu : " + contenu.substring(0, Math.min(50, contenu.length())) + "...");
        
        // Test 2: Upload de fichier vers un peer
        System.out.println("\n📤 Safy uploade 'nouveau_fichier.txt' vers Ben...");
        boolean uploadReussi = safy.uploaderFichierVersPeer("nouveau_fichier.txt", "localhost", 8002);
        resultatTest(uploadReussi, "Upload vers peer distant réussi");
        
        // Vérifier que Ben a bien reçu le fichier
        Thread.sleep(2000);
        List<String> fichiersBen = ben.listerFichiersPeerDistant("localhost", 8002);
        boolean fichierRecu = fichiersBen.stream().anyMatch(f -> f.contains("nouveau_fichier"));
        resultatTest(fichierRecu, "Fichier reçu chez Ben");
        
        // Test 3: Suppression de fichier
        System.out.println("\n🗑️ Suppression d'un fichier chez Fatou...");
        // D'abord créer un fichier à supprimer
        File aSupprimer = new File(fatou.getDossierPartage(), "fichier_a_supprimer.txt");
        try (FileOutputStream fos = new FileOutputStream(aSupprimer)) {
            fos.write("Fichier temporaire".getBytes());
        }
        boolean supprime = fatou.supprimerFichier("fichier_a_supprimer.txt");
        resultatTest(supprime, "Fichier supprimé avec succès");
        
        // Test 4: Statistiques des peers
        System.out.println("\n📊 Statistiques des peers...");
        System.out.println("Statistiques Safy : " + safy.getStatistiques());
        System.out.println("Statistiques Ben : " + ben.getStatistiques());
        resultatTest(true, "Statistiques récupérées");
        
        System.out.println();
    }
    
    private static void testRobustesse() throws Exception {
        System.out.println("=== 6. Test de robustesse ===");
        
        // Test 1: Fichier avec nom spécial (accents, espaces)
        System.out.println("\n🔤 Création et téléchargement fichier avec nom spécial...");
        File fichierSpecial = new File(moussa.getDossierPartage(), "fichier spécial éèç àù.txt");
        try (FileOutputStream fos = new FileOutputStream(fichierSpecial)) {
            fos.write("Contenu avec accents : éèêë àâä ùûü çć".getBytes("UTF-8"));
        }
        
        // Télécharger le fichier avec nom spécial
        boolean downloadSpecial = diallo.telechargerFichierDepuisPeer("fichier spécial éèç àù.txt", "localhost", 8004);
        resultatTest(downloadSpecial, "Téléchargement fichier avec nom spécial");
        
        // Test 2: Peer temporairement inactif
        System.out.println("\n⏸️ Test avec peer temporairement arrêté...");
        aimerou.arreter();
        Thread.sleep(2000);
        
        boolean downloadPeerInactif = safy.telechargerFichierDepuisPeer("test1.txt", "localhost", 8003);
        resultatTest(!downloadPeerInactif, "Erreur gérée : peer inactif");
        
        // Redémarrer le peer
        aimerou = new PeerAimerou();
        aimerou.demarrer().get(5, TimeUnit.SECONDS);
        Thread.sleep(3000);
        resultatTest(aimerou.estActif(), "Peer redémarré avec succès");
        
        // Test 3: Téléchargements multiples simultanés
        System.out.println("\n⚡ Test de téléchargements simultanés...");
        CompletableFuture<Boolean> download1 = CompletableFuture.supplyAsync(() -> 
            ben.telechargerFichierDepuisPeer("test1.txt", "localhost", 8001));
        CompletableFuture<Boolean> download2 = CompletableFuture.supplyAsync(() -> 
            fatou.telechargerFichierDepuisPeer("document.txt", "localhost", 8001));
        CompletableFuture<Boolean> download3 = CompletableFuture.supplyAsync(() -> 
            diallo.telechargerFichierDepuisPeer("readme.md", "localhost", 8001));
        
        CompletableFuture<Void> allDownloads = CompletableFuture.allOf(download1, download2, download3);
        allDownloads.get(30, TimeUnit.SECONDS);
        
        boolean tous_reussis = download1.get() && download2.get() && download3.get();
        resultatTest(tous_reussis, "Téléchargements simultanés réussis");
        
        System.out.println();
    }
    
    private static void testPerformance() throws Exception {
        System.out.println("=== 7. Test de performance ===");
        
        // Test 1: Création d'un fichier volumineux
        System.out.println("\n📂 Création d'un fichier volumineux (10 Mo)...");
        File bigFile = new File(diallo.getDossierPartage(), "bigfile.dat");
        long startCreate = System.currentTimeMillis();
        
        try (FileOutputStream fos = new FileOutputStream(bigFile)) {
            byte[] buffer = new byte[1024 * 1024]; // 1 Mo
            for (int i = 0; i < 10; i++) {
                fos.write(buffer);
            }
        }
        
        long createTime = System.currentTimeMillis() - startCreate;
        resultatTest(bigFile.exists() && bigFile.length() > 10_000_000, 
                    "Fichier volumineux créé en " + createTime + " ms");
        
        // Test 2: Téléchargement du fichier volumineux
        System.out.println("\n📥 Téléchargement du fichier volumineux...");
        long startDownload = System.currentTimeMillis();
        boolean bigDownload = ben.telechargerFichierDepuisPeer("bigfile.dat", "localhost", 8006);
        long downloadTime = System.currentTimeMillis() - startDownload;
        
        resultatTest(bigDownload, "Fichier volumineux téléchargé en " + downloadTime + " ms");
        
        // Test 3: Vérification de l'intégrité
        System.out.println("\n🔒 Vérification d'intégrité du fichier volumineux...");
        long startVerif = System.currentTimeMillis();
        
        File originalFile = new File(diallo.getDossierPartage(), "bigfile.dat");
        File downloadedFile = new File(ben.getDossierPartage(), "bigfile.dat");
        
        String checksumOriginal = diallo.getFileManager().calculerChecksum(originalFile);
        String checksumDownload = ben.getFileManager().calculerChecksum(downloadedFile);
        
        long verifTime = System.currentTimeMillis() - startVerif;
        boolean integrite = checksumOriginal.equals(checksumDownload);
        
        resultatTest(integrite, "Intégrité vérifiée en " + verifTime + " ms");
        
        System.out.println();
    }
    
    // === MÉTHODES UTILITAIRES ===
    
    private static void afficherFichiersInitiaux() {
        System.out.println("\n📂 Fichiers initiaux de chaque peer :");
        afficherFichiers(safy);
        afficherFichiers(ben);
        afficherFichiers(aimerou);
        afficherFichiers(moussa);
        afficherFichiers(fatou);
        afficherFichiers(diallo);
    }
    
    private static void afficherFichiers(Peer peer) {
        System.out.println("  " + peer.getPseudo() + " (" + peer.getDossierPartage().getPath() + ") :");
        List<File> fichiers = peer.getFileManager().listerFichiers();
        if (fichiers.isEmpty()) {
            System.out.println("    (aucun fichier)");
        } else {
            fichiers.stream()
                .filter(File::isFile)
                .forEach(f -> System.out.println("    - " + f.getName() + " (" + formatTaille(f.length()) + ")"));
        }
    }
    
    private static String formatTaille(long octets) {
        if (octets < 1024) return octets + " B";
        if (octets < 1024 * 1024) return String.format("%.1f KB", octets / 1024.0);
        if (octets < 1024 * 1024 * 1024) return String.format("%.1f MB", octets / (1024.0 * 1024));
        return String.format("%.1f GB", octets / (1024.0 * 1024 * 1024));
    }
    
    private static void resultatTest(boolean condition, String message) {
        String emoji = condition ? "✅" : "❌";
        String status = condition ? "SUCCÈS" : "ÉCHEC";
        System.out.println(emoji + " [" + status + "] " + message);
        
        if (!condition) {
            System.err.println("  ⚠️  Ce test a échoué !");
        }
    }
    
    private static void arreterTousLesPeers() {
        System.out.println("\n🛑 Arrêt de tous les peers...");
        
        if (safy != null && safy.estActif()) safy.arreter();
        if (ben != null && ben.estActif()) ben.arreter();
        if (aimerou != null && aimerou.estActif()) aimerou.arreter();
        if (moussa != null && moussa.estActif()) moussa.arreter();
        if (fatou != null && fatou.estActif()) fatou.arreter();
        if (diallo != null && diallo.estActif()) diallo.arreter();
        
        // Attendre un peu pour que tous les threads se terminent proprement
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("✅ Tous les peers sont arrêtés");
    }
}