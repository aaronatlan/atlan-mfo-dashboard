package com.atlan.mfo.model.enums;

/** Étape d'une opportunité dans le pipeline. */
public enum DealStatus {
    INITIAL_REVIEW("Revue initiale"),
    SCREENING("Screening"),
    DUE_DILIGENCE("Due diligence"),
    IC_VOTE("Vote IC"),
    APPROVED("Approuvé"),
    DECLINED_LOST("Décliné / perdu");

    private final String label;

    DealStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Une opportunité est « active » tant qu'elle n'est ni approuvée ni perdue. */
    public boolean isActive() {
        return this != APPROVED && this != DECLINED_LOST;
    }
}
