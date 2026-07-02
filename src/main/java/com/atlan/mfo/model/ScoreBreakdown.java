package com.atlan.mfo.model;

import com.atlan.mfo.model.enums.Tier;

import java.util.List;

/**
 * Résultat du scoring d'une opportunité : composants, points acquis / possibles,
 * score entier plafonné et tier dérivé (voir §5).
 */
public record ScoreBreakdown(
        List<ScoreComponent> components,
        double earned,
        double possible,
        int score,
        Tier tier) {

    /** Sous-score d'un composant par libellé, ou {@code null} s'il est exclu (persistance sub_*). */
    public Double subScoreOf(String label) {
        return components.stream()
                .filter(c -> c.label().equals(label) && c.communicated())
                .map(ScoreComponent::subScore)
                .findFirst()
                .orElse(null);
    }
}

