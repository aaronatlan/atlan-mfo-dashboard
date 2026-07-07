package com.atlan.mfo.model.enums;

/**
 * État réel d'une opportunité décidée, une fois l'issue connue (boucle prédit →
 * réalisé). Sert à comparer la décision/le score prédit au résultat effectif.
 */
public enum OutcomeState {
    IN_PROGRESS("In progress"),
    EXITED("Exited"),
    WRITTEN_OFF("Written off"),
    DID_NOT_INVEST("Did not invest");

    private final String label;

    OutcomeState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
