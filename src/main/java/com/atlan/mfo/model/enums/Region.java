package com.atlan.mfo.model.enums;

/**
 * Région d'investissement ciblée par une opportunité (multi-sélection). Distincte de la
 * géographie du siège du GP ({@code geography}, un pays) : une opportunité peut viser
 * plusieurs régions indépendamment de la localisation de son gérant. Persistée en CSV de
 * codes (comme les autres multi-sélections de {@link Classification}).
 */
public enum Region {
    NORTH_AMERICA("North America"),
    EUROPE("Europe"),
    UK("United Kingdom"),
    MENA("Middle East & North Africa"),
    ASIA_PACIFIC("Asia-Pacific"),
    LATIN_AMERICA("Latin America"),
    AFRICA("Africa"),
    GLOBAL("Global");

    private final String label;

    Region(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
