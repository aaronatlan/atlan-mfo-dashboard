package com.atlan.mfo.model;

import com.atlan.mfo.model.enums.Role;

/**
 * Utilisateur applicatif (table {@code app_user}).
 *
 * <p>Ne porte jamais le hash du mot de passe : celui-ci reste confiné à la
 * couche d'authentification.
 */
public record AppUser(
        long id,
        String username,
        String fullName,
        Role role,
        boolean active,
        boolean mustChangePassword) {

    public boolean isPartner() {
        return role == Role.PARTNER;
    }

    public boolean isAnalyst() {
        return role == Role.ANALYST;
    }
}
