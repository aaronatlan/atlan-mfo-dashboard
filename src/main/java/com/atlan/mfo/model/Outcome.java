package com.atlan.mfo.model;

import com.atlan.mfo.model.enums.OutcomeState;

/**
 * Résultat réel d'une opportunité décidée, avec l'instantané de ce qui était
 * <b>prédit</b> au moment de la saisie (score, retours attendus) — boucle de
 * calibration prédit → réalisé (idée « boucler la boucle »).
 *
 * <p>Auto-suffisant : conserve nom/stratégie/prédit pour survivre à une
 * réinitialisation du pipeline et alimenter le jeu de données dans la durée.
 */
public record Outcome(
        Long id,
        String kind,                 // "FUND" | "DEAL"
        long opportunityId,
        String name,
        String strategy,
        Integer predictedScore,
        Double expectedIrr,          // attendu à la décision (fraction) — deals
        Double expectedMoic,
        OutcomeState outcome,
        Double realizedIrr,          // réalisé (fraction)
        Double realizedMoic,
        Double realizedDpi,
        String note) {

    /** Un résultat chiffré est disponible (au moins un réalisé renseigné). */
    public boolean hasRealized() {
        return realizedIrr != null || realizedMoic != null || realizedDpi != null;
    }
}
