-- ============================================================
-- LogiStock SA : création de la base de données (PostgreSQL 14+)
-- Fichier 1/2 : structure, règles métier, vues
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------- Tables ----------

CREATE TABLE role (
  id_role  SERIAL PRIMARY KEY,
  libelle  VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE utilisateur (
  id_utilisateur     SERIAL PRIMARY KEY,
  nom                VARCHAR(100) NOT NULL,
  email              VARCHAR(150) NOT NULL UNIQUE,
  mot_de_passe_hache VARCHAR(255) NOT NULL,
  actif              BOOLEAN NOT NULL DEFAULT TRUE,
  id_role            INTEGER NOT NULL REFERENCES role(id_role)
);

CREATE TABLE categorie (
  id_categorie SERIAL PRIMARY KEY,
  nom          VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE produit (
  id_produit     SERIAL PRIMARY KEY,
  reference      VARCHAR(30)  NOT NULL UNIQUE,
  designation    VARCHAR(150) NOT NULL,
  prix           NUMERIC(10,2) NOT NULL CHECK (prix >= 0),
  quantite_stock INTEGER NOT NULL DEFAULT 0 CHECK (quantite_stock >= 0),
  seuil_alerte   INTEGER NOT NULL DEFAULT 0 CHECK (seuil_alerte >= 0),
  id_categorie   INTEGER NOT NULL REFERENCES categorie(id_categorie)
);

CREATE TABLE client (
  id_client SERIAL PRIMARY KEY,
  nom       VARCHAR(100) NOT NULL,
  prenom    VARCHAR(100),
  email     VARCHAR(150) UNIQUE,
  telephone VARCHAR(30),
  adresse   VARCHAR(255)
);

CREATE TABLE commande (
  id_commande    SERIAL PRIMARY KEY,
  date_commande  TIMESTAMP NOT NULL DEFAULT NOW(),
  statut         VARCHAR(10) NOT NULL DEFAULT 'BROUILLON'
                 CHECK (statut IN ('BROUILLON','VALIDEE','ANNULEE')),
  total          NUMERIC(12,2) NOT NULL DEFAULT 0,
  id_client      INTEGER NOT NULL REFERENCES client(id_client),
  id_utilisateur INTEGER NOT NULL REFERENCES utilisateur(id_utilisateur)
);

CREATE TABLE ligne_commande (
  id_commande   INTEGER REFERENCES commande(id_commande) ON DELETE CASCADE,
  id_produit    INTEGER REFERENCES produit(id_produit),
  quantite      INTEGER NOT NULL CHECK (quantite > 0),
  prix_unitaire NUMERIC(10,2) NOT NULL CHECK (prix_unitaire >= 0),
  PRIMARY KEY (id_commande, id_produit)
);

CREATE TABLE facture (
  id_facture    SERIAL PRIMARY KEY,
  numero        VARCHAR(30) NOT NULL UNIQUE,
  date_emission TIMESTAMP NOT NULL DEFAULT NOW(),
  montant       NUMERIC(12,2) NOT NULL,
  chemin_pdf    VARCHAR(255),
  id_commande   INTEGER NOT NULL UNIQUE REFERENCES commande(id_commande)
);

CREATE TABLE mouvement_stock (
  id_mouvement   SERIAL PRIMARY KEY,
  date_heure     TIMESTAMP NOT NULL DEFAULT NOW(),
  type           VARCHAR(6) NOT NULL CHECK (type IN ('ENTREE','SORTIE')),
  quantite       INTEGER NOT NULL CHECK (quantite > 0),
  id_produit     INTEGER NOT NULL REFERENCES produit(id_produit),
  id_utilisateur INTEGER NOT NULL REFERENCES utilisateur(id_utilisateur),
  id_commande    INTEGER REFERENCES commande(id_commande)
);

-- ---------- Index (recherches et KPI) ----------

CREATE INDEX idx_produit_categorie  ON produit(id_categorie);
CREATE INDEX idx_commande_client    ON commande(id_client);
CREATE INDEX idx_commande_statut    ON commande(statut, date_commande);
CREATE INDEX idx_mouvement_produit  ON mouvement_stock(id_produit, date_heure);
CREATE INDEX idx_ligne_produit      ON ligne_commande(id_produit);

-- ---------- Règle : un mouvement met à jour le stock ----------
-- ENTREE : stock + quantité. SORTIE : stock - quantité, refusée si le stock
-- deviendrait négatif (le stock n'est jamais négatif).

CREATE FUNCTION appliquer_mouvement() RETURNS trigger AS $$
DECLARE
  stock_actuel INTEGER;
BEGIN
  SELECT quantite_stock INTO stock_actuel
    FROM produit WHERE id_produit = NEW.id_produit FOR UPDATE;

  IF NEW.type = 'SORTIE' THEN
    IF stock_actuel < NEW.quantite THEN
      RAISE EXCEPTION 'Stock insuffisant pour le produit % (disponible : %, demandé : %)',
        NEW.id_produit, stock_actuel, NEW.quantite
        USING ERRCODE = 'check_violation';
    END IF;
    UPDATE produit SET quantite_stock = quantite_stock - NEW.quantite
      WHERE id_produit = NEW.id_produit;
  ELSE
    UPDATE produit SET quantite_stock = quantite_stock + NEW.quantite
      WHERE id_produit = NEW.id_produit;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mouvement_stock
  BEFORE INSERT ON mouvement_stock
  FOR EACH ROW EXECUTE FUNCTION appliquer_mouvement();

-- L'historique est intangible : pas de modification ni de suppression.
CREATE FUNCTION interdire_modif_mouvement() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'Un mouvement de stock ne peut être ni modifié ni supprimé';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mouvement_intangible
  BEFORE UPDATE OR DELETE ON mouvement_stock
  FOR EACH ROW EXECUTE FUNCTION interdire_modif_mouvement();

-- ---------- Règle : le total d'une commande suit ses lignes ----------
-- Les lignes ne sont modifiables que tant que la commande est en BROUILLON.

CREATE FUNCTION maj_total_commande() RETURNS trigger AS $$
DECLARE
  id_c INTEGER := COALESCE(NEW.id_commande, OLD.id_commande);
  st   VARCHAR(10);
BEGIN
  SELECT statut INTO st FROM commande WHERE id_commande = id_c;
  IF st IS NOT NULL AND st <> 'BROUILLON' THEN
    RAISE EXCEPTION 'Les lignes de la commande % ne sont plus modifiables (statut %)', id_c, st;
  END IF;
  UPDATE commande
     SET total = COALESCE((SELECT SUM(quantite * prix_unitaire)
                             FROM ligne_commande WHERE id_commande = id_c), 0)
   WHERE id_commande = id_c;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_total_commande
  AFTER INSERT OR UPDATE OR DELETE ON ligne_commande
  FOR EACH ROW EXECUTE FUNCTION maj_total_commande();

-- ---------- Règle : valider une vente (une seule transaction) ----------
-- 1. refuse si la commande n'est pas un brouillon ou n'a aucune ligne
-- 2. crée une sortie de stock par produit (refusée si stock insuffisant)
-- 3. passe la commande à VALIDEE
-- 4. crée la facture (le PDF est généré ensuite par le backend)
-- Si une étape échoue, tout est annulé.

CREATE FUNCTION valider_commande(p_id_commande INTEGER, p_id_utilisateur INTEGER)
RETURNS INTEGER AS $$
DECLARE
  cmd   commande%ROWTYPE;
  ligne RECORD;
  id_f  INTEGER;
BEGIN
  SELECT * INTO cmd FROM commande WHERE id_commande = p_id_commande FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Commande % introuvable', p_id_commande;
  END IF;
  IF cmd.statut <> 'BROUILLON' THEN
    RAISE EXCEPTION 'La commande % est déjà %', p_id_commande, cmd.statut;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM ligne_commande WHERE id_commande = p_id_commande) THEN
    RAISE EXCEPTION 'La commande % ne contient aucune ligne', p_id_commande;
  END IF;

  FOR ligne IN
    SELECT id_produit, quantite FROM ligne_commande
     WHERE id_commande = p_id_commande ORDER BY id_produit
  LOOP
    INSERT INTO mouvement_stock (type, quantite, id_produit, id_utilisateur, id_commande)
    VALUES ('SORTIE', ligne.quantite, ligne.id_produit, p_id_utilisateur, p_id_commande);
  END LOOP;

  UPDATE commande SET statut = 'VALIDEE' WHERE id_commande = p_id_commande;

  INSERT INTO facture (numero, montant, id_commande)
  VALUES ('FAC-' || to_char(NOW(), 'YYYY') || '-' || lpad(p_id_commande::text, 5, '0'),
          cmd.total, p_id_commande)
  RETURNING id_facture INTO id_f;

  RETURN id_f;
END;
$$ LANGUAGE plpgsql;

-- ---------- Vues utiles au tableau de bord ----------

-- Produits en alerte ou en rupture
CREATE VIEW v_produits_en_alerte AS
SELECT p.id_produit, p.reference, p.designation, c.nom AS categorie,
       p.quantite_stock, p.seuil_alerte,
       CASE WHEN p.quantite_stock = 0 THEN 'RUPTURE' ELSE 'ALERTE' END AS etat
  FROM produit p JOIN categorie c USING (id_categorie)
 WHERE p.quantite_stock < p.seuil_alerte OR p.quantite_stock = 0;

-- Palmarès des produits les plus vendus (commandes validées)
CREATE VIEW v_produits_plus_vendus AS
SELECT p.id_produit, p.reference, p.designation,
       SUM(l.quantite) AS quantite_vendue,
       SUM(l.quantite * l.prix_unitaire) AS chiffre_affaires
  FROM ligne_commande l
  JOIN commande c USING (id_commande)
  JOIN produit p USING (id_produit)
 WHERE c.statut = 'VALIDEE'
 GROUP BY p.id_produit, p.reference, p.designation
 ORDER BY quantite_vendue DESC;

-- Chiffre d'affaires par jour (commandes validées)
CREATE VIEW v_chiffre_affaires_jour AS
SELECT date_commande::date AS jour, COUNT(*) AS nb_ventes, SUM(total) AS chiffre_affaires
  FROM commande WHERE statut = 'VALIDEE'
 GROUP BY date_commande::date ORDER BY jour;

-- Chiffre d'affaires par catégorie (indicateur supplémentaire possible)
CREATE VIEW v_chiffre_affaires_categorie AS
SELECT cat.nom AS categorie, SUM(l.quantite * l.prix_unitaire) AS chiffre_affaires
  FROM ligne_commande l
  JOIN commande c USING (id_commande)
  JOIN produit p USING (id_produit)
  JOIN categorie cat USING (id_categorie)
 WHERE c.statut = 'VALIDEE'
 GROUP BY cat.nom ORDER BY chiffre_affaires DESC;
