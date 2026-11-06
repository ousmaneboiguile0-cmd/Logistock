-- ============================================================
-- LogiStock SA - Données d'exemple seulement (4 par table)
-- À exécuter sur la base logistock déjà créée (tables existantes)
-- ============================================================

USE logistock;

-- Vide d'abord les tables (la structure reste) pour éviter les doublons
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE mouvement_stock;
TRUNCATE TABLE facture;
TRUNCATE TABLE ligne_commande;
TRUNCATE TABLE commande;
TRUNCATE TABLE client;
TRUNCATE TABLE produit;
TRUNCATE TABLE categorie;
TRUNCATE TABLE utilisateur;
TRUNCATE TABLE role;
SET FOREIGN_KEY_CHECKS = 1;

-- ROLE
INSERT INTO role (libelle) VALUES
  ('ADMIN'),
  ('GESTIONNAIRE'),
  ('COMMERCIAL'),
  ('MAGASINIER');

-- UTILISATEUR (mots de passe fictifs : en vrai, on stocke un hash)
INSERT INTO utilisateur (nom, email, mot_de_passe_hache, actif, id_role) VALUES
  ('Admin Principal', 'admin@logistock.ml',    'HASH_A_REMPLACER', TRUE,  1),
  ('Awa Traoré',      'awa@logistock.ml',      'HASH_A_REMPLACER', TRUE,  2),
  ('Moussa Keita',    'moussa@logistock.ml',   'HASH_A_REMPLACER', TRUE,  3),
  ('Seydou Diarra',   'seydou@logistock.ml',   'HASH_A_REMPLACER', TRUE,  4);

-- CATEGORIE
INSERT INTO categorie (nom) VALUES
  ('Informatique'),
  ('Bureautique'),
  ('Mobilier'),
  ('Accessoires');

-- PRODUIT (prix en FCFA)
INSERT INTO produit (reference, designation, prix, quantite_stock, seuil_alerte, id_categorie) VALUES
  ('P001', 'Clavier USB',        12500.00,  48, 10, 1),
  ('P002', 'Imprimante laser',   85000.00,  19,  5, 1),
  ('P003', 'Ramette papier A4',   3500.00, 190, 50, 2),
  ('P004', 'Chaise de bureau',   45000.00,  28,  5, 3);

-- CLIENT
INSERT INTO client (nom, prenom, email, telephone, adresse) VALUES
  ('Diallo',    'Fatoumata', 'fatoumata.diallo@mail.ml',    '+223 70 00 00 01', 'Bamako, Hamdallaye'),
  ('Coulibaly', 'Ibrahim',   'ibrahim.coulibaly@mail.ml',   '+223 70 00 00 02', 'Bamako, Badalabougou'),
  ('Sangaré',   'Mariam',    'mariam.sangare@mail.ml',      '+223 70 00 00 03', 'Bamako, Magnambougou'),
  ('Camara',    'Oumar',     'oumar.camara@mail.ml',        '+223 70 00 00 04', 'Kati');

-- COMMANDE
INSERT INTO commande (date_commande, statut, total, id_client, id_utilisateur) VALUES
  ('2026-11-01 09:30:00', 'VALIDEE',  25000.00, 1, 3),
  ('2026-11-02 11:15:00', 'VALIDEE',  85000.00, 2, 3),
  ('2026-11-03 14:00:00', 'VALIDEE',  35000.00, 3, 3),
  ('2026-11-04 10:45:00', 'VALIDEE',  90000.00, 4, 3);

-- LIGNE_COMMANDE (une ligne par commande ici)
INSERT INTO ligne_commande (id_commande, id_produit, quantite, prix_unitaire) VALUES
  (1, 1,  2, 12500.00),   -- 2 x Clavier USB      = 25000
  (2, 2,  1, 85000.00),   -- 1 x Imprimante laser = 85000
  (3, 3, 10,  3500.00),   -- 10 x Ramette A4      = 35000
  (4, 4,  2, 45000.00);   -- 2 x Chaise de bureau = 90000

-- FACTURE (une facture par commande)
INSERT INTO facture (numero, date_emission, montant, chemin_pdf, id_commande) VALUES
  ('FAC-2026-0001', '2026-11-01', 25000.00, 'factures/FAC-2026-0001.pdf', 1),
  ('FAC-2026-0002', '2026-11-02', 85000.00, 'factures/FAC-2026-0002.pdf', 2),
  ('FAC-2026-0003', '2026-11-03', 35000.00, 'factures/FAC-2026-0003.pdf', 3),
  ('FAC-2026-0004', '2026-11-04', 90000.00, 'factures/FAC-2026-0004.pdf', 4);

-- MOUVEMENT_STOCK (2 entrées sans commande, 2 sorties liées à une commande)
INSERT INTO mouvement_stock (date_heure, type, quantite, id_produit, id_utilisateur, id_commande) VALUES
  ('2026-10-30 08:00:00', 'ENTREE', 50, 1, 2, NULL),
  ('2026-10-30 08:30:00', 'ENTREE', 20, 2, 2, NULL),
  ('2026-11-01 09:35:00', 'SORTIE',  2, 1, 4, 1),
  ('2026-11-02 11:20:00', 'SORTIE',  1, 2, 4, 2);

-- Vérification : nombre de lignes par table
SELECT 'role' AS table_nom, COUNT(*) AS nb_lignes FROM role
UNION ALL SELECT 'utilisateur',    COUNT(*) FROM utilisateur
UNION ALL SELECT 'categorie',      COUNT(*) FROM categorie
UNION ALL SELECT 'produit',        COUNT(*) FROM produit
UNION ALL SELECT 'client',         COUNT(*) FROM client
UNION ALL SELECT 'commande',       COUNT(*) FROM commande
UNION ALL SELECT 'ligne_commande', COUNT(*) FROM ligne_commande
UNION ALL SELECT 'facture',        COUNT(*) FROM facture
UNION ALL SELECT 'mouvement_stock',COUNT(*) FROM mouvement_stock;
