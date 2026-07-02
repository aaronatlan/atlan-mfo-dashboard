package com.atlan.mfo.model.enums;

/** Famille d'un fonds (voir §1). */
public enum Category {
    BUYOUT_GROWTH_VC("Buyout, growth, VC"),
    SECONDARIES("Secondaries"),
    PRIVATE_CREDIT("Private credit");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
