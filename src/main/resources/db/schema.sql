-- Atlan MFO Dashboard — schéma PostgreSQL (idempotent).
-- Voir §4.2 et §13 de la spécification.

-- Enums (CREATE TYPE ne supporte pas IF NOT EXISTS → DO block)
DO $$ BEGIN
    CREATE TYPE category AS ENUM ('BUYOUT_GROWTH_VC','SECONDARIES','PRIVATE_CREDIT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE deal_status AS ENUM ('INITIAL_REVIEW','SCREENING','DUE_DILIGENCE','IC_VOTE','APPROVED','DECLINED_LOST');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE benchmark_status AS ENUM ('ABOVE_THRESHOLD','BELOW_THRESHOLD','NA');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE app_role AS ENUM ('ANALYST','PARTNER');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Utilisateurs applicatifs
CREATE TABLE IF NOT EXISTS app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name     TEXT NOT NULL,
    role          app_role NOT NULL DEFAULT 'ANALYST',
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,  -- forcé au 1er login (§13.3)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fonds : Buyout/growth/VC, Secondaries, Private credit
CREATE TABLE IF NOT EXISTS fund_investment (
    id             BIGSERIAL PRIMARY KEY,
    category       category NOT NULL,
    name           TEXT NOT NULL,
    next_steps     TEXT,
    status         deal_status NOT NULL DEFAULT 'INITIAL_REVIEW',
    vs_benchmark   benchmark_status DEFAULT 'NA',
    geography      TEXT,                    -- token canonique : 'US','EUROPE','UK','GLOBAL','OTHER' (§13.1)
    asset_class    TEXT,
    commitment     NUMERIC,                 -- capital envisagé (devise native ci-dessous) — agrégats convertis en USD
    currency       TEXT NOT NULL DEFAULT 'USD',  -- devise native du commitment (code ISO)

    -- Les millésimes (track record complet, N par fonds) sont dans fund_vintage.

    -- timeline
    first_close    DATE,
    final_close    DATE,

    comments       TEXT,

    -- contact de l'opportunité (GP, sponsor, intermédiaire…)
    contact_name   TEXT,
    contact_email  TEXT,
    contact_phone  TEXT,


    version        BIGINT NOT NULL DEFAULT 0,   -- verrou optimiste (§13.2)
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT REFERENCES app_user(id)
);

-- Millésimes d'un fonds : track record complet, N par fonds (§5.5)
CREATE TABLE IF NOT EXISTS fund_vintage (
    id           BIGSERIAL PRIMARY KEY,
    fund_id      BIGINT NOT NULL REFERENCES fund_investment(id) ON DELETE CASCADE,
    vintage_year INT NOT NULL,
    dpi          NUMERIC,
    tvpi         NUMERIC,
    irr          NUMERIC,                 -- fraction : 0.137 = 13.7 %
    moic         NUMERIC,
    UNIQUE (fund_id, vintage_year)
);

-- Deals directs : Co-investissement et direct
CREATE TABLE IF NOT EXISTS direct_deal (
    id             BIGSERIAL PRIMARY KEY,
    name           TEXT NOT NULL,
    next_steps     TEXT,
    status         deal_status NOT NULL DEFAULT 'INITIAL_REVIEW',
    vs_benchmark   benchmark_status DEFAULT 'NA',
    industry       TEXT,
    gp             TEXT,                    -- general partner / sponsor
    geography      TEXT,                    -- token canonique (§13.1)
    inv_type       TEXT,                    -- ex. 'Direct/Growth Equity'
    commitment     NUMERIC,                 -- capital envisagé (devise native ci-dessous) — agrégats convertis en USD
    currency       TEXT NOT NULL DEFAULT 'USD',  -- devise native du commitment (code ISO)

    -- performance financière (fractions pour les %)
    revenue        NUMERIC,
    cagr_pct       NUMERIC,                 -- 0.47 = 47 %
    ebitda         NUMERIC,
    ebitda_gr_pct  NUMERIC,
    ebitda_mgn_pct NUMERIC,                 -- 0.92 = 92 %
    fcf            NUMERIC,
    fcf_conv_pct   NUMERIC,
    ev             NUMERIC,

    -- retours attendus
    entry_mult     NUMERIC,
    peers_mult     TEXT,                    -- fourchette informative, ex. '20-40x'
    exit_val       NUMERIC,
    exp_irr_pct    NUMERIC,
    exp_moic       NUMERIC,

    -- timeline
    deal_deadline  DATE,
    target_exit    DATE,

    comments       TEXT,

    -- contact de l'opportunité (GP, sponsor, intermédiaire…)
    contact_name   TEXT,
    contact_email  TEXT,
    contact_phone  TEXT,

    version        BIGINT NOT NULL DEFAULT 0,   -- verrou optimiste (§13.2)
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT REFERENCES app_user(id)
);

-- Ajouts additifs idempotents (pour les bases créées avant l'ajout de colonnes)
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS commitment NUMERIC;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS commitment NUMERIC;

-- Contact par opportunité
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS contact_name  TEXT;
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS contact_email TEXT;
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS contact_phone TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS contact_name  TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS contact_email TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS contact_phone TEXT;

-- Devise native par opportunité (USD = référence, agrégats convertis en USD)
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'USD';
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'USD';

-- Classification marchés privés (structure Patrimium). Sur fund_investment, la colonne
-- asset_class (existante) porte désormais le code de classe (PRIVATE_EQUITY, VENTURE_CAPITAL,
-- PRIVATE_CREDIT, REAL_ASSETS, SECONDARIES) ; la sous-stratégie détaillée passe dans
-- sub_strategy. Sur direct_deal, asset_class est ajoutée. access_route (single) détermine
-- le template de fiche. secondary_mandate / underlying_strategy = CSV (secondaires).
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS sub_strategy        TEXT;
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS access_route        TEXT;
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS secondary_mandate   TEXT;
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS underlying_strategy TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS asset_class         TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS sub_strategy        TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS access_route        TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS secondary_mandate   TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS underlying_strategy TEXT;

-- Régions d'investissement ciblées (multi-sélection, CSV de codes Region) — distinctes de
-- geography (pays du siège du GP). Sur fonds et deals.
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS target_regions TEXT;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS target_regions TEXT;

-- Par millésime : taille du fonds levé + cible de levée, et cash yield (private credit).
ALTER TABLE fund_vintage ADD COLUMN IF NOT EXISTS fund_size    NUMERIC;
ALTER TABLE fund_vintage ADD COLUMN IF NOT EXISTS target_raise NUMERIC;
ALTER TABLE fund_vintage ADD COLUMN IF NOT EXISTS cash_yield   NUMERIC;

-- Nom du GP / gérant (distinct du nom du fonds). Les deals ont déjà la colonne gp.
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS gp_name TEXT;

-- Paramètres de scoring modifiables (méthodologie éditable, §5). Clé → valeur ;
-- si une clé est absente, le moteur utilise sa valeur par défaut.
CREATE TABLE IF NOT EXISTS scoring_param (
    name  TEXT PRIMARY KEY,
    value NUMERIC NOT NULL
);

-- Taux de change éditables vers l'USD (devise de référence, §4) : valeur d'1 unité
-- de la devise en USD. Repli sur les défauts du code si une devise est absente.
CREATE TABLE IF NOT EXISTS fx_rate (
    currency     TEXT PRIMARY KEY,        -- code ISO (EUR, GBP, AED…)
    usd_per_unit NUMERIC NOT NULL,        -- 1 unité de `currency` = usd_per_unit USD
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed des taux (idempotent) : ne réécrit pas une valeur déjà ajustée par l'utilisateur.
INSERT INTO fx_rate (currency, usd_per_unit) VALUES
    ('USD', 1.0), ('EUR', 1.08), ('GBP', 1.27), ('AED', 0.2723), ('CHF', 1.11),
    ('CAD', 0.73), ('AUD', 0.66), ('JPY', 0.0067), ('ILS', 0.27)
ON CONFLICT (currency) DO NOTHING;

