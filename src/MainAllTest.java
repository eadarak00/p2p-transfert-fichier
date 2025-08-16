import clients.*;
import entities.Peer;
import entities.PeerInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MainAllTest {

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
            
            // --- 8. Tests de cas limites supplémentaires ---
            testCasLimites();
            
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
    
    private static void testCasLimites() throws Exception {
        System.out.println("=== 8. Test de cas limites supplémentaires ===");
        
        // Test 1: Upload d'un fichier inexistant
        System.out.println("\n📤 Test upload fichier inexistant...");
        boolean uploadInexistant = safy.uploaderFichierVersPeer("fichier_qui_nexiste_pas.txt", "localhost", 8002);
        resultatTest(!uploadInexistant, "Erreur gérée : upload fichier inexistant");
        
        // Test 2: Upload vers peer inexistant
        System.out.println("\n📤 Test upload vers peer inexistant...");
        File fichierPourUpload = new File(safy.getDossierPartage(), "fichier_pour_test_upload.txt");
        try (FileOutputStream fos = new FileOutputStream(fichierPourUpload)) {
            fos.write("Test upload".getBytes());
        }
        boolean uploadPeerInexistant = safy.uploaderFichierVersPeer("fichier_pour_test_upload.txt", "localhost", 9999);
        resultatTest(!uploadPeerInexistant, "Erreur gérée : upload vers peer inexistant");
        
        // Test 3: Suppression fichier inexistant
        System.out.println("\n🗑️ Test suppression fichier inexistant...");
        boolean suppressionInexistant = fatou.supprimerFichier("fichier_qui_nexiste_pas.txt");
        resultatTest(!suppressionInexistant, "Erreur gérée : suppression fichier inexistant");
        
        // Test 4: Test avec nom de fichier null/vide
        System.out.println("\n📝 Test avec noms de fichiers invalides...");
        boolean downloadNull = ben.telechargerFichierDepuisPeer(null, "localhost", 8001);
        resultatTest(!downloadNull, "Erreur gérée : nom fichier null");
        
        boolean downloadVide = ben.telechargerFichierDepuisPeer("", "localhost", 8001);
        resultatTest(!downloadVide, "Erreur gérée : nom fichier vide");
        
        // Test 5: Test avec port invalide
        System.out.println("\n🔌 Test avec port invalide...");
        List<String> fichiersPortInvalide = ben.listerFichiersPeerDistant("localhost", -1);
        resultatTest(fichiersPortInvalide.isEmpty(), "Erreur gérée : port invalide");
        
        // Test 6: Test de synchronisation après ajout/suppression de fichiers
        System.out.println("\n🔄 Test synchronisation après modifications...");
        
        // Ajouter un fichier chez Diallo
        File nouveauFichierSync = new File(diallo.getDossierPartage(), "fichier_sync_test.txt");
        try (FileOutputStream fos = new FileOutputStream(nouveauFichierSync)) {
            fos.write("Test de synchronisation".getBytes());
        }
        
        // Forcer la synchronisation
        diallo.mettreAJourCacheComplet();
        safy.synchroniserMaintenant();
        Thread.sleep(5000);
        
        // Vérifier que les autres peers peuvent le trouver
        List<String> peersAvecNouveauFichierSync = safy.rechercherFichier("fichier_sync_test.txt");
        resultatTest(!peersAvecNouveauFichierSync.isEmpty(), "Synchronisation automatique après ajout fichier");
        
        // Test 7: Test intégrité avec fichier corrompu simulé
        System.out.println("\n🔒 Test vérification intégrité...");
        File fichierTest = new File(safy.getDossierPartage(), "test1.txt");
        if (fichierTest.exists()) {
            String checksumCorrect = safy.getFileManager().calculerChecksum(fichierTest);
            boolean integriteCorrecte = safy.getFileManager().verifierIntegrite(fichierTest, checksumCorrect);
            resultatTest(integriteCorrecte, "Vérification intégrité avec checksum correct");
            
            boolean integriteFausse = safy.getFileManager().verifierIntegrite(fichierTest, "checksum_bidon");
            resultatTest(!integriteFausse, "Détection d'intégrité compromise avec mauvais checksum");
        }
        
        // Test 8: Test avec beaucoup de peers simultanés (simulation)
        System.out.println("\n👥 Test gestion multiple peers simultanés...");
        boolean gestionMultiple = true;
        try {
            // Tous les peers essaient de se synchroniser en même temps
            CompletableFuture<Void> syncMultiple = CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> safy.synchroniserMaintenant()),
                CompletableFuture.runAsync(() -> ben.synchroniserMaintenant()),
                CompletableFuture.runAsync(() -> aimerou.synchroniserMaintenant()),
                CompletableFuture.runAsync(() -> moussa.synchroniserMaintenant()),
                CompletableFuture.runAsync(() -> fatou.synchroniserMaintenant()),
                CompletableFuture.runAsync(() -> diallo.synchroniserMaintenant())
            );
            
            syncMultiple.get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            gestionMultiple = false;
        }
        resultatTest(gestionMultiple, "Gestion synchronisation multiple simultanée");
        
        // Test 9: Test récupération après erreur réseau (simulation)
        System.out.println("\n🌐 Test récupération après problème réseau...");
        // Tenter plusieurs connexions pour simuler instabilité réseau
        int tentatives = 0;
        int reussites = 0;
        for (int i = 0; i < 10; i++) {
            tentatives++;
            try {
                List<String> fichiers = ben.listerFichiersPeerDistant("localhost", 8001);
                if (!fichiers.isEmpty()) {
                    reussites++;
                }
                Thread.sleep(100);
            } catch (Exception e) {
                // Simuler erreur réseau
            }
        }
        resultatTest(reussites >= 7, "Récupération après problèmes réseau : " + reussites + "/" + tentatives + " réussites");
        
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
        
        // Test de détection automatique du nouveau fichier
        System.out.println("\n🔍 Test de détection automatique du nouveau fichier...");
        safy.mettreAJourCacheComplet();
        Thread.sleep(3000);
        
        // Ben recherche le nouveau fichier
        List<String> peersAvecNouveauFichier = ben.rechercherFichier("nouveau_fichier.txt");
        resultatTest(!peersAvecNouveauFichier.isEmpty(), "Nouveau fichier détecté automatiquement par les autres peers");
        
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
        
        // Test 3: Suppression de fichier puis tentative d'accès
        System.out.println("\n🗑️ Suppression d'un fichier chez Fatou puis test d'accès...");
        // D'abord créer un fichier à supprimer
        File aSupprimer = new File(fatou.getDossierPartage(), "fichier_a_supprimer.txt");
        try (FileOutputStream fos = new FileOutputStream(aSupprimer)) {
            fos.write("Fichier temporaire".getBytes());
        }
        
        // Vérifier qu'il existe d'abord
        String contenuAvantSuppression = fatou.lireFichier("fichier_a_supprimer.txt");
        resultatTest(contenuAvantSuppression.contains("temporaire"), "Fichier lisible avant suppression");
        
        // Supprimer
        boolean supprime = fatou.supprimerFichier("fichier_a_supprimer.txt");
        resultatTest(supprime, "Fichier supprimé avec succès");
        
        // Tenter de lire après suppression
        try {
            fatou.lireFichier("fichier_a_supprimer.txt");
            resultatTest(false, "Devrait lever une exception après suppression");
        } catch (IOException e) {
            resultatTest(true, "Exception correctement levée après suppression");
        }
        
        // Test 4: Statistiques des peers
        System.out.println("\n📊 Statistiques des peers...");
        System.out.println("Statistiques Safy : " + safy.getStatistiques());
        System.out.println("Statistiques Ben : " + ben.getStatistiques());
        resultatTest(true, "Statistiques récupérées");
        
        System.out.println();
    }
    
    private static void testRobustesse() throws Exception {
        System.out.println("=== 6. Test de robustesse ===");
        
        // Test 1: Fichier avec nom spécial (accents, espaces, caractères spéciaux)
        System.out.println("\n🔤 Création et téléchargement fichier avec nom spécial...");
        File fichierSpecial = new File(moussa.getDossierPartage(), "fichier spécial éèç àù#@$%.txt");
        try (FileOutputStream fos = new FileOutputStream(fichierSpecial)) {
            fos.write("Contenu avec accents : éèêë àâä ùûü çć et caractères spéciaux #@$%".getBytes("UTF-8"));
        }
        
        // Télécharger le fichier avec nom spécial
        boolean downloadSpecial = diallo.telechargerFichierDepuisPeer("fichier spécial éèç àù#@$%.txt", "localhost", 8004);
        resultatTest(downloadSpecial, "Téléchargement fichier avec nom spécial et caractères spéciaux");
        
        // Test 2: Fichier avec nom très long
        System.out.println("\n📏 Test fichier avec nom très long...");
        String nomTresLong = "fichier_avec_un_nom_extremement_long_qui_pourrait_poser_des_problemes_de_compatibilite_sur_certains_systemes_de_fichiers.txt";
        File fichierLong = new File(fatou.getDossierPartage(), nomTresLong);
        try (FileOutputStream fos = new FileOutputStream(fichierLong)) {
            fos.write("Contenu du fichier avec nom très long".getBytes());
        }
        boolean downloadLong = ben.telechargerFichierDepuisPeer(nomTresLong, "localhost", 8005);
        resultatTest(downloadLong, "Téléchargement fichier avec nom très long");
        
        // Test 3: Fichier de taille 0 (vide)
        System.out.println("\n🗋 Test fichier vide...");
        File fichierVide = new File(aimerou.getDossierPartage(), "fichier_vide.txt");
        fichierVide.createNewFile(); // Créer un fichier vide
        boolean downloadVide = safy.telechargerFichierDepuisPeer("fichier_vide.txt", "localhost", 8003);
        resultatTest(downloadVide, "Téléchargement fichier vide");
        
        // Test 4: Peer temporairement inactif
        System.out.println("\n⏸️ Test avec peer temporairement arrêté...");
        moussa.arreter();
        Thread.sleep(2000);
        
        boolean downloadPeerInactif = safy.telechargerFichierDepuisPeer("test1.txt", "localhost", 8004);
        resultatTest(!downloadPeerInactif, "Erreur gérée : peer inactif");
        
        // Redémarrer le peer
        moussa = new PeerMoussa();
        moussa.demarrer().get(5, TimeUnit.SECONDS);
        Thread.sleep(3000);
        resultatTest(moussa.estActif(), "Peer redémarré avec succès");
        
        // Test 5: Téléchargements multiples simultanés (stress test)
        System.out.println("\n⚡ Stress test : téléchargements simultanés depuis plusieurs peers...");
        CompletableFuture<Boolean> download1 = CompletableFuture.supplyAsync(() -> 
            ben.telechargerFichierDepuisPeer("test1.txt", "localhost", 8001));
        CompletableFuture<Boolean> download2 = CompletableFuture.supplyAsync(() -> 
            fatou.telechargerFichierDepuisPeer("document.txt", "localhost", 8001));
        CompletableFuture<Boolean> download3 = CompletableFuture.supplyAsync(() -> 
            diallo.telechargerFichierDepuisPeer("readme.md", "localhost", 8001));
        CompletableFuture<Boolean> download4 = CompletableFuture.supplyAsync(() -> 
            moussa.telechargerFichier("test1.txt")); // Via recherche réseau
        CompletableFuture<Boolean> download5 = CompletableFuture.supplyAsync(() -> 
            aimerou.telechargerFichier("document.txt")); // Via recherche réseau
        
        CompletableFuture<Void> allDownloads = CompletableFuture.allOf(download1, download2, download3, download4, download5);
        allDownloads.get(45, TimeUnit.SECONDS);
        
        int reussites = 0;
        if (download1.get()) reussites++;
        if (download2.get()) reussites++;
        if (download3.get()) reussites++;
        if (download4.get()) reussites++;
        if (download5.get()) reussites++;
        
        resultatTest(reussites >= 3, "Stress test réussi : " + reussites + "/5 téléchargements simultanés OK");
        
        // Test 6: Test limite de connexions
        System.out.println("\n🔄 Test de reconnexions multiples...");
        boolean reconnexionOK = true;
        for (int i = 0; i < 5; i++) {
            List<String> fichiers = ben.listerFichiersPeerDistant("localhost", 8001);
            if (fichiers.isEmpty()) {
                reconnexionOK = false;
                break;
            }
            Thread.sleep(500);
        }
        resultatTest(reconnexionOK, "Reconnexions multiples successives OK");
        
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