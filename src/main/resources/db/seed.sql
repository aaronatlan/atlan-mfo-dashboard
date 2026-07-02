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
