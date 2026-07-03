-- Atlan MFO Dashboard — rôle applicatif à privilèges minimaux (production).
--
-- À exécuter UNE FOIS, par un superuser / propriétaire de la base, à l'installation
-- (après schema.sql). L'application se connecte ensuite avec ce rôle « atlan_app »,
-- qui ne peut ni créer/supprimer des tables ni administrer la base : uniquement
-- lire/écrire les données métier (voir DEPLOYMENT.md, §2).
--
-- ⚠️  Remplacer 'CHANGER_CE_MOT_DE_PASSE' par un mot de passe fort avant exécution.

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'atlan_app') THEN
    CREATE ROLE atlan_app LOGIN PASSWORD 'CHANGER_CE_MOT_DE_PASSE';
  END IF;
END $$;

-- Connexion + accès au schéma (adapter le nom de base si différent de atlan_mfo)
GRANT CONNECT ON DATABASE atlan_mfo TO atlan_app;
GRANT USAGE ON SCHEMA public TO atlan_app;

-- Données métier : lecture/écriture uniquement (pas de DDL)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO atlan_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO atlan_app;

-- Mêmes privilèges pour les objets futurs créés par le propriétaire
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO atlan_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO atlan_app;
