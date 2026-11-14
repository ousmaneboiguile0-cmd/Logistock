-- ============================================================
-- LogiStock SA : données de départ et jeu de démonstration
-- Fichier 2/2 : à exécuter après 01_schema.sql
-- ============================================================

-- Rôles (obligatoires)
INSERT INTO role (libelle) VALUES
  ('Administrateur'), ('Gestionnaire de stock'), ('Commercial');

-- Comptes de démonstration. Mot de passe de tous les comptes : Passw0rd!
-- (haché avec bcrypt ; à changer avant toute mise en ligne)
INSERT INTO utilisateur (nom, email, mot_de_passe_hache, id_role) VALUES
  ('Admin LogiStock',   'admin@logistock.test',   crypt('Passw0rd!', gen_salt('bf')), 1),
  ('Awa Traoré',        'stock@logistock.test',   crypt('Passw0rd!', gen_salt('bf')), 2),
  ('Moussa Diarra',     'vente@logistock.test',   crypt('Passw0rd!', gen_salt('bf')), 3);

INSERT INTO categorie (nom) VALUES
  ('Matériel de bureau'), ('Consommables'), ('Petit équipement');

INSERT INTO produit (reference, designation, prix, seuil_alerte, id_categorie) VALUES
  ('PAP-A4-500', 'Ramette papier A4 500 feuilles', 4.50, 20, 2),
  ('STY-BLEU',   'Stylo bille bleu (boîte de 50)', 9.90, 10, 2),
  ('ENC-HP305',  'Cartouche d''encre HP 305',     24.00, 5,  2),
  ('AGR-PRO',    'Agrafeuse professionnelle',     12.50, 5,  1),
  ('CLA-A4',     'Classeur à levier A4',           3.20, 15, 1),
  ('CAL-SCI',    'Calculatrice scientifique',     18.90, 5,  3),
  ('LAM-LED',    'Lampe de bureau LED',           29.90, 3,  3);

INSERT INTO client (nom, prenom, email, telephone, adresse) VALUES
  ('Diallo',  'Fatoumata', 'f.diallo@exemple.test', '+223 70 00 00 01', 'Bamako, Hamdallaye'),
  ('Keita',   'Ibrahim',   'i.keita@exemple.test',  '+223 70 00 00 02', 'Bamako, Badalabougou'),
  ('Société Sahel Fournitures', NULL, 'contact@sahel.test', '+223 20 00 00 03', 'Bamako, ACI 2000');

-- Entrées de stock initiales (par le gestionnaire de stock, id 2).
-- Le trigger met à jour quantite_stock.
INSERT INTO mouvement_stock (type, quantite, id_produit, id_utilisateur) VALUES
  ('ENTREE', 200, 1, 2),
  ('ENTREE',  60, 2, 2),
  ('ENTREE',  12, 3, 2),
  ('ENTREE',  25, 4, 2),
  ('ENTREE',  80, 5, 2),
  ('ENTREE',  10, 6, 2),
  ('ENTREE',   4, 7, 2);

-- Une vente validée (commercial, id 3) : Fatoumata achète deux catégories
INSERT INTO commande (id_client, id_utilisateur) VALUES (1, 3);          -- commande 1
INSERT INTO ligne_commande VALUES (1, 1, 10, 4.50), (1, 4, 2, 12.50);
SELECT valider_commande(1, 3);

-- Une deuxième vente validée
INSERT INTO commande (id_client, id_utilisateur) VALUES (3, 3);          -- commande 2
INSERT INTO ligne_commande VALUES (2, 3, 8, 24.00), (2, 6, 4, 18.90), (2, 7, 3, 29.90);
SELECT valider_commande(2, 3);

-- Un brouillon encore en cours de saisie
INSERT INTO commande (id_client, id_utilisateur) VALUES (2, 3);          -- commande 3
INSERT INTO ligne_commande VALUES (3, 5, 5, 3.20);
