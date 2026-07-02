package com.atlan.mfo.model.enums;

/**
 * Niveau de confiance dérivé du score (non stocké en base, voir §5.6).
 * Strong 70–95, Moderate 40–69, Caution 0–39.
 */
public enum Tier {
    STRONG("Strong"),
    MODERATE("Moderate"),
    CAUTION("Caution");

    private final String label;

    Tier(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Dérive le tier d'un score entier. */
    public static Tier fromScore(int score) {
        if (score >= 70) {
            return STRONG;
        }
        if (score >= 40) {
            return MODERATE;
        }
        return CAUTION;
    }
}
