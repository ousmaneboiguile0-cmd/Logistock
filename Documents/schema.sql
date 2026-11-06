-- ============================================================
-- LogiStock SA - Schéma de la base de données (MySQL / MariaDB)
-- Création des 9 tables (les données d'exemple sont dans seed.sql)
-- Compatible XAMPP (phpMyAdmin) et MySQL Workbench
-- ============================================================

DROP DATABASE IF EXISTS logistock;
CREATE DATABASE logistock CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE logistock;

-- ---------- 1. Création des tables ----------

CREATE TABLE role (
  id_role INT AUTO_INCREMENT PRIMARY KEY,
  libelle VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE utilisateur (
  id_utilisateur INT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  mot_de_passe_hache VARCHAR(255) NOT NULL,
  actif BOOLEAN NOT NULL DEFAULT TRUE,
  id_role INT NOT NULL,
  CONSTRAINT fk_utilisateur_role
    FOREIGN KEY (id_role) REFERENCES role(id_role)
) ENGINE=InnoDB;

CREATE TABLE categorie (
  id_categorie INT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(80) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE produit (
  id_produit INT AUTO_INCREMENT PRIMARY KEY,
  reference VARCHAR(30) NOT NULL UNIQUE,
  designation VARCHAR(150) NOT NULL,
  prix DECIMAL(10,2) NOT NULL CHECK (prix >= 0),
  quantite_stock INT NOT NULL DEFAULT 0 CHECK (quantite_stock >= 0),
  seuil_alerte INT NOT NULL DEFAULT 0 CHECK (seuil_alerte >= 0),
  id_categorie INT NOT NULL,
  CONSTRAINT fk_produit_categorie
    FOREIGN KEY (id_categorie) REFERENCES categorie(id_categorie)
) ENGINE=InnoDB;

CREATE TABLE client (
  id_client INT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(100) NOT NULL,
  prenom VARCHAR(100),
  email VARCHAR(150) UNIQUE,
  telephone VARCHAR(30),
  adresse VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE commande (
  id_commande INT AUTO_INCREMENT PRIMARY KEY,
  date_commande TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  statut VARCHAR(10) NOT NULL
    CHECK (statut IN ('BROUILLON','VALIDEE','ANNULEE')),
  total DECIMAL(12,2) NOT NULL DEFAULT 0,
  id_client INT NOT NULL,
  id_utilisateur INT NOT NULL,
  CONSTRAINT fk_commande_client
    FOREIGN KEY (id_client) REFERENCES client(id_client),
  CONSTRAINT fk_commande_utilisateur
    FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur)
) ENGINE=InnoDB;

CREATE TABLE ligne_commande (
  id_commande INT NOT NULL,
  id_produit INT NOT NULL,
  quantite INT NOT NULL CHECK (quantite > 0),
  prix_unitaire DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (id_commande, id_produit),
  CONSTRAINT fk_ligne_commande
    FOREIGN KEY (id_commande) REFERENCES commande(id_commande),
  CONSTRAINT fk_ligne_produit
    FOREIGN KEY (id_produit) REFERENCES produit(id_produit)
) ENGINE=InnoDB;

CREATE TABLE facture (
  id_facture INT AUTO_INCREMENT PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  date_emission DATE NOT NULL,
  montant DECIMAL(12,2) NOT NULL,
  chemin_pdf VARCHAR(255),
  id_commande INT NOT NULL UNIQUE,
  CONSTRAINT fk_facture_commande
    FOREIGN KEY (id_commande) REFERENCES commande(id_commande)
) ENGINE=InnoDB;

CREATE TABLE mouvement_stock (
  id_mouvement INT AUTO_INCREMENT PRIMARY KEY,
  date_heure TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  type VARCHAR(6) NOT NULL CHECK (type IN ('ENTREE','SORTIE')),
  quantite INT NOT NULL CHECK (quantite > 0),
  id_produit INT NOT NULL,
  id_utilisateur INT NOT NULL,
  id_commande INT NULL,
  CONSTRAINT fk_mouvement_produit
    FOREIGN KEY (id_produit) REFERENCES produit(id_produit),
  CONSTRAINT fk_mouvement_utilisateur
    FOREIGN KEY (id_utilisateur) REFERENCES utilisateur(id_utilisateur),
  CONSTRAINT fk_mouvement_commande
    FOREIGN KEY (id_commande) REFERENCES commande(id_commande)
) ENGINE=InnoDB;
