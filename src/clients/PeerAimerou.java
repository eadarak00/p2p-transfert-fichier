package clients;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import entities.Peer;

public class PeerAimerou extends Peer {

    public PeerAimerou() {
        super("Aimerou", 8003, "./uploads/Aimerou");

        // Assurez-vous que "dossier" est un File (comme dans Peer)
        File dossier = new File("./uploads/Aimerou");
        dossier.mkdirs(); // Crée le dossier s'il n'existe pas

        try {
            File fichier1 = new File(dossier, "test1.txt");
            File fichier2 = new File(dossier, "document.txt");
            File fichier3 = new File(dossier, "readme.md");

            if (!fichier1.exists()) {
                Files.write(fichier1.toPath(),
                        ("Fichier de test 1\nCréé par " + dossier.getName() +
                                "\nContenu pour démonstration P2P\n" +
                                "Timestamp: " + System.currentTimeMillis()).getBytes());
            }

            if (!fichier2.exists()) {
                Files.write(fichier2.toPath(),
                        ("Document exemple\n" +
                                "Ce fichier peut être partagé entre peers\n" +
                                "Ligne 3\nLigne 4\nLigne 5\n").getBytes());
            }

            if (!fichier3.exists()) {
                Files.write(fichier3.toPath(),
                        ("# README\n\n" +
                                "Ce dossier contient les fichiers partagés de " + dossier.getName() + "\n\n" +
                                "## Instructions\n\n" +
                                "1. Démarrer le peer\n" +
                                "2. Se connecter à d'autres peers\n" +
                                "3. Partager et télécharger des fichiers\n").getBytes());
            }

        } catch (IOException e) {
            System.err.println("Erreur lors de la création des fichiers de test: " + e.getMessage());
        }
    }
}
