package com.atlan.mfo.model.enums;

/** Position d'une opportunité par rapport au seuil de référence. */
public enum BenchmarkStatus {
    ABOVE_THRESHOLD("Au-dessus du seuil"),
    BELOW_THRESHOLD("Sous le seuil"),
    NA("N/A");

    private final String label;

    BenchmarkStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
