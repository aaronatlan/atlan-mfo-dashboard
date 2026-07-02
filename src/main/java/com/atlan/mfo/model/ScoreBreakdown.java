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
}
