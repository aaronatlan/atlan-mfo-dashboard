package com.atlan.mfo.auth;

import com.atlan.mfo.model.AppUser;

/**
 * Session applicative : conserve l'utilisateur authentifié et son rôle pour la
 * durée d'exécution (voir §7).
 */
public final class Session {

    private static AppUser currentUser;

    private Session() {
    }

    public static void setCurrentUser(AppUser user) {
        currentUser = user;
    }

    public static AppUser currentUser() {
        return currentUser;
    }

    public static boolean isAuthenticated() {
        return currentUser != null;
    }

    public static void clear() {
        currentUser = null;
    }
}
