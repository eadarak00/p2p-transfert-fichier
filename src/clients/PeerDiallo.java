package clients;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import entities.Peer;

public class PeerDiallo extends Peer {

    public PeerDiallo() {
        super("Diallo", 8006, "./uploads/Diallo");

        File dossier = new File("./uploads/Diallo");
        dossier.mkdirs();

        try {
            File fichier1 = new File(dossier, "diallo_todo.txt");
            File fichier2 = new File(dossier, "rapport.docx");
            File fichier3 = new File(dossier, "readme.md");

            if (!fichier1.exists()) {
                Files.write(fichier1.toPath(),
                        ("Tâches de Diallo\nPeer-to-peer test\nTimestamp: " + System.currentTimeMillis()).getBytes());
            }

            if (!fichier2.exists()) {
                Files.write(fichier2.toPath(),
                        ("DOCX factice - contenu de test peer-to-peer").getBytes());
            }

            if (!fichier3.exists()) {
                Files.write(fichier3.toPath(),
                        ("# README Diallo\nContenu partagé par Diallo.").getBytes());
            }

        } catch (IOException e) {
            System.err.println("Erreur lors de la création des fichiers de Diallo: " + e.getMessage());
        }
    }
}
