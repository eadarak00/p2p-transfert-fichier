package clients;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import entities.Peer;

public class PeerMoussa extends Peer {

    public PeerMoussa() {
        super("Moussa", 8004, "./uploads/Moussa");

        File dossier = new File("./uploads/Moussa");
        dossier.mkdirs();

        try {
            File fichier1 = new File(dossier, "moussa_notes.txt");
            File fichier2 = new File(dossier, "cours_java.pdf");
            File fichier3 = new File(dossier, "readme.md");

            if (!fichier1.exists()) {
                Files.write(fichier1.toPath(),
                        ("Notes de Moussa\nExemple peer-to-peer\nTimestamp: " + System.currentTimeMillis()).getBytes());
            }

            if (!fichier2.exists()) {
                Files.write(fichier2.toPath(),
                        ("PDF factice - contenu de test peer-to-peer").getBytes());
            }

            if (!fichier3.exists()) {
                Files.write(fichier3.toPath(),
                        ("# README Moussa\nContenu partagé par Moussa.").getBytes());
            }

        } catch (IOException e) {
            System.err.println("Erreur lors de la création des fichiers de Moussa: " + e.getMessage());
        }
    }
}
