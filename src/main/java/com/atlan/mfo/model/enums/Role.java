package com.atlan.mfo.model.enums;

/** Rôle applicatif d'un utilisateur (voir §7). */
public enum Role {
    /** Accès complet lecture/écriture, vue analyste. */
    ANALYST,
    /** Lecture seule, verrouillé en mode présentation. */
    PARTNER
}
