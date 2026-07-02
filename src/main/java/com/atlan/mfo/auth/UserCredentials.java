package com.atlan.mfo.auth;

import com.atlan.mfo.model.AppUser;

/**
 * Association d'un utilisateur et de son hash de mot de passe, confinée à la
 * couche d'authentification (le hash ne remonte jamais vers l'UI).
 */
public record UserCredentials(AppUser user, String passwordHash) {
}
