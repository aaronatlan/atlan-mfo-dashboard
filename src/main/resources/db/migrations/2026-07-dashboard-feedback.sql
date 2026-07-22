-- Migration — retours dashboard de juillet 2026 (v1.5.0).
-- À exécuter UNE FOIS sur la base de production, par le propriétaire (neondb_owner,
-- p. ex. via l'éditeur SQL Neon), AVANT de déployer la v1.5.0 sur les postes : la
-- nouvelle version lit ces colonnes. Additif et idempotent — sans effet si déjà appliqué,
-- et sans impact sur les anciennes versions installées (elles ignorent les colonnes en trop).
--
-- Ces mêmes instructions figurent aussi dans schema.sql (source de vérité) ; ce fichier
-- n'est qu'un extrait prêt à coller.

-- #1 Régions d'investissement ciblées (multi-sélection, CSV de codes Region).
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS target_regions TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS target_regions TEXT;

-- #2 / #3 Par millésime : taille du fonds levé, cible de levée, cash yield (private credit).
ALTER TABLE fund_vintage ADD COLUMN IF NOT EXISTS fund_size    NUMERIC;
ALTER TABLE fund_vintage ADD COLUMN IF NOT EXISTS target_raise NUMERIC;
ALTER TABLE fund_vintage ADD COLUMN IF NOT EXISTS cash_yield   NUMERIC;

-- #4 Nom du GP / gérant, distinct du nom du fonds.
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS gp_name TEXT;

-- #5 (Hedge Fund) : aucune migration — la classe d'actifs est stockée en texte (asset_class).
-- #6 / #7 : aucune migration — filtres et graphes ne touchent pas au schéma.
