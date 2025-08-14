# Système de Transfert de Fichiers P2P

##  Description

Ce projet est un **système peer-to-peer (P2P)** permettant le partage de fichiers entre plusieurs machines **sans serveur central**.  
Chaque machine (appelée *peer*) peut :
- Partager ses fichiers locaux,
- Rechercher des fichiers sur d’autres peers,
- Télécharger directement des fichiers depuis un ou plusieurs peers,
- Uploader ses propres fichiers vers d’autres peers.


##  Fonctionnalités principales

- **Découverte automatique des peers** sur le réseau.
- **Partage de fichiers** à partir d’un dossier local.
- **Téléchargement direct** entre peers (pas de serveur central).
- **Transferts multiples** gérés avec des threads.
- **Interface graphique** en Java Swing pour gérer facilement les échanges.

## Architecture

```
src/
├── clients/           # Classes des peers spécifiques
├── entities/          # Entités et modèles (Peer, PeerInfo, etc.)
├── gui/               # Interface graphique Swing
uploads/
├── public/            # Fichiers reçus depuis d'autres peers
├── <NomPeer>/         # Dossier local des fichiers partagés par le peer
```


## Installation

1. **Cloner le projet**
   ```bash
   git clone https://github.com/eadarak00/p2p-transfert-fichier.git
   cd p2p-transfert-fichier
   ```


2. **Compiler le projet**

   ```bash
   javac -d bin src/**/*.java
   ```

3. **Lancer un peer**

   ```bash
   java -cp bin clients.PeerSafy
   ```

   (ou le peer de votre choix)

##  Utilisation

1. **Démarrer plusieurs peers** (chacun sur un port différent).
2. **Choisir un dossier partagé** dans chaque peer.
3. **Découvrir les autres peers** via la fonction "Recherche de peers".
4. **Télécharger ou envoyer** des fichiers en cliquant sur le nom du peer et en sélectionnant un fichier.


##  Logique du P2P

* Chaque peer joue **le rôle de client et de serveur**.
* Les **listes de fichiers** sont échangées entre pairs.
* Les transferts sont faits **directement** entre machines.
* Aucune machine n’est **obligatoirement maîtresse**.


## Configuration

* **Port par défaut** : défini dans la classe du peer (`clients.PeerXYZ`).
* **Dossier partagé** : défini dans le constructeur du peer.
* **Timeout** : 2 secondes pour la découverte de peers silencieux.


## Licence

Projet éducatif — libre d’utilisation pour l’apprentissage.


