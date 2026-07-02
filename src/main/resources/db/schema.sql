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
    geography      TEXT,                    -- token canonique : 'US','EUROPE','UK','DACH','GLOBAL','OTHER' (§13.1)
    asset_class    TEXT,
    commitment     NUMERIC,                 -- capital envisagé par Atlan (devise de base) — KPI « capital en revue »

    -- Les millésimes (track record complet, N par fonds) sont dans fund_vintage.

    -- timeline
    first_close    DATE,
    final_close    DATE,

    comments       TEXT,

    -- snapshot du score au dernier enregistrement (affichage = recalcul live)
    score_snapshot INT,
    sub_dpi        NUMERIC,
    sub_irr        NUMERIC,
    sub_moic       NUMERIC,
    sub_geo        NUMERIC,
    sub_time       NUMERIC,

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
    commitment     NUMERIC,                 -- capital envisagé par Atlan (devise de base) — KPI « capital en revue »

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

    score_snapshot INT,
    sub_cagr       NUMERIC,
    sub_ebitda_mgn NUMERIC,
    sub_fcf        NUMERIC,
    sub_irr        NUMERIC,
    sub_geo        NUMERIC,
    sub_time       NUMERIC,

    version        BIGINT NOT NULL DEFAULT 0,   -- verrou optimiste (§13.2)
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT REFERENCES app_user(id)
);

-- Ajouts additifs idempotents (pour les bases créées avant l'ajout de colonnes)
ALTER TABLE fund_investment ADD COLUMN IF NOT EXISTS commitment NUMERIC;
ALTER TABLE direct_deal     ADD COLUMN IF NOT EXISTS commitment NUMERIC;
