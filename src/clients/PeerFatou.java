package clients;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import entities.Peer;

public class PeerFatou extends Peer {

    public PeerFatou() {
        super("Fatou", 8005, "./uploads/Fatou");

        File dossier = new File("./uploads/Fatou");
        dossier.mkdirs();

        try {
            File fichier1 = new File(dossier, "fatou_resume.txt");
            File fichier2 = new File(dossier, "presentation.pptx");
            File fichier3 = new File(dossier, "readme.md");

            if (!fichier1.exists()) {
                Files.write(fichier1.toPath(),
                        ("Résumé de Fatou\nProjet P2P\nTimestamp: " + System.currentTimeMillis()).getBytes());
            }

            if (!fichier2.exists()) {
                Files.write(fichier2.toPath(),
                        ("PPT factice - contenu de test peer-to-peer").getBytes());
            }

            if (!fichier3.exists()) {
                Files.write(fichier3.toPath(),
                        ("# README Fatou\nContenu partagé par Fatou.").getBytes());
            }

        } catch (IOException e) {
            System.err.println("Erreur lors de la création des fichiers de Fatou: " + e.getMessage());
        }
    }
}
