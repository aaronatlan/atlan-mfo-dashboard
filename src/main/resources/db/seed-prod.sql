-- Atlan MFO Dashboard — SEED PRODUCTION.
-- Sélectionné quand db.seed=prod. Crée uniquement le compte administrateur initial,
-- AUCUNE donnée de démonstration.
--
-- Mot de passe temporaire : "Atlan-Setup-2026"  (must_change_password = TRUE →
-- changement obligatoire au premier login, §13.3). À changer immédiatement.
-- Les autres utilisateurs (analystes, partners) se créent ensuite via
-- scripts/user-add.sh (voir DEPLOYMENT.md).
INSERT INTO app_user (username, password_hash, full_name, role, must_change_password)
VALUES (
    'admin',
    '$2y$12$FY1e1no9CwfFLKrRvuhlsezffhyGEa/PfGjUi6FEbt.0s08yDGX1u',
    'Administrateur',
    'ANALYST',
    TRUE
)
ON CONFLICT (username) DO NOTHING;
