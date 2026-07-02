package com.atlan.mfo.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Hachage et vérification des mots de passe via BCrypt
 * (bibliothèque {@code at.favre.lib:bcrypt}).
 */
public final class PasswordHasher {

    private static final int COST = 12;

    private PasswordHasher() {
    }

    /** Produit un hash BCrypt ($2a$) du mot de passe. */
    public static String hash(char[] password) {
        return BCrypt.withDefaults().hashToString(COST, password);
    }

    /**
     * Vérifie un mot de passe contre un hash stocké.
     * Compatible avec les variantes $2a$, $2b$ et $2y$.
     */
    public static boolean verify(char[] password, String hash) {
        BCrypt.Result result = BCrypt.verifyer().verify(password, hash);
        return result.verified;
    }
}
