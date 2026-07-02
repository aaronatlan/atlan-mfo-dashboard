-- Atlan MFO Dashboard — données de démarrage (idempotent).
-- Compte administrateur initial. Mot de passe temporaire : "admin".
-- must_change_password = TRUE → changement forcé au 1er login (§13.3).
-- Hash BCrypt ($2y$12$…) de "admin".
INSERT INTO app_user (username, password_hash, full_name, role, must_change_password)
VALUES (
    'admin',
    '$2y$12$oMut5ZuWWA9DKz0vKUzFAu62XSVRtGvYT5X2hxtxN4nG5Oadyiw.i',
    'Administrateur',
    'ANALYST',
    TRUE
)
ON CONFLICT (username) DO NOTHING;

-- Fonds fictifs (insérés une seule fois, si la table est vide)
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM fund_investment) THEN
    INSERT INTO fund_investment
      (category, name, status, vs_benchmark, geography, asset_class, commitment,
       recent_vintage, recent_dpi, recent_tvpi, recent_irr, recent_moic,
       earlier_vintage, earlier_dpi, earlier_tvpi, earlier_irr, earlier_moic,
       first_close, final_close, score_snapshot)
    VALUES
      ('BUYOUT_GROWTH_VC','Meridian Buyout Fund IV','DUE_DILIGENCE','ABOVE_THRESHOLD','US','Buyout',25000000,
       2021,0.65,1.9,0.24,2.1, 2018,1.10,2.2,0.19,2.0, DATE '2025-09-01', DATE '2025-12-15', 85),
      ('BUYOUT_GROWTH_VC','Northwind Growth Partners III','SCREENING','NA','EUROPE','Growth',15000000,
       2022,0.20,1.4,0.18,1.6, 2019,0.80,1.9,0.17,1.8, NULL, DATE '2026-03-01', 62),
      ('BUYOUT_GROWTH_VC','Helix Ventures Fund II','INITIAL_REVIEW','BELOW_THRESHOLD','US','Venture',10000000,
       2023,0.05,1.2,0.12,1.3, NULL,NULL,NULL,NULL,NULL, NULL, NULL, 44),
      ('SECONDARIES','Lattice Secondaries Fund','IC_VOTE','ABOVE_THRESHOLD','GLOBAL','Secondaries',20000000,
       2020,0.90,1.7,0.21,1.8, 2017,1.30,1.9,0.18,1.9, NULL, DATE '2025-08-01', 78),
      ('PRIVATE_CREDIT','Anchor Direct Lending II','DUE_DILIGENCE','ABOVE_THRESHOLD','US','Private credit',30000000,
       2021,0.55,1.3,0.14,1.4, 2019,0.90,1.25,0.12,1.3, NULL, DATE '2026-01-20', 71),
      ('PRIVATE_CREDIT','Sable Credit Opportunities','DECLINED_LOST','BELOW_THRESHOLD','UK','Private credit',12000000,
       2022,0.10,1.05,0.07,1.1, NULL,NULL,NULL,NULL,NULL, NULL, NULL, 33);
  END IF;
END $$;

-- Deals directs fictifs (insérés une seule fois, si la table est vide)
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM direct_deal) THEN
    INSERT INTO direct_deal
      (name, status, vs_benchmark, industry, gp, geography, inv_type, commitment,
       revenue, cagr_pct, ebitda, ebitda_gr_pct, ebitda_mgn_pct, fcf, fcf_conv_pct, ev,
       entry_mult, peers_mult, exit_val, exp_irr_pct, exp_moic,
       deal_deadline, target_exit, score_snapshot)
    VALUES
      ('Project Orion','DUE_DILIGENCE','ABOVE_THRESHOLD','Software','Vertex Capital','US','Direct/Growth Equity',40000000,
       120000000,0.47,30000000,0.50,0.25,20000000,0.70,800000000, 12,'20-40x',1600000000,0.32,2.4,
       DATE '2025-10-01', DATE '2029-01-01', 80),
      ('Project Vega','SCREENING','NA','Healthcare','Cedar Partners','EUROPE','Co-investment',18000000,
       60000000,0.30,12000000,0.35,0.20,6000000,0.50,300000000, 10,NULL,550000000,0.22,1.9,
       DATE '2026-02-15', NULL, 58),
      ('Project Atlas','INITIAL_REVIEW','BELOW_THRESHOLD','Industrials','Forge Equity','DACH','Direct',22000000,
       200000000,0.12,25000000,0.08,0.125,8000000,0.40,500000000, 9,NULL,NULL,0.15,1.5,
       NULL, NULL, 41),
      ('Project Lyra','IC_VOTE','ABOVE_THRESHOLD','Fintech','Quill Ventures','UK','Direct/Growth Equity',28000000,
       45000000,0.55,5000000,0.60,0.11,2000000,0.40,350000000, 15,NULL,980000000,0.35,2.8,
       DATE '2025-11-10', DATE '2030-06-01', 76);
  END IF;
END $$;
