package com.atlan.mfo.model.enums;

import java.util.Locale;

/**
 * Devises supportées. Le dollar américain ({@link #USD}) est la <b>devise de
 * référence</b> : tous les agrégats (total commitment, capital under review,
 * allocation…) sont convertis en USD (voir §4). Chaque opportunité conserve sa
 * devise native pour l'affichage.
 *
 * <p>Le taux par défaut ({@code defaultUsdPerUnit}) ne sert qu'au <b>seed</b> initial
 * de la table {@code fx_rate} ; les taux effectifs sont ensuite éditables dans l'app.
 */
public enum Currency {

    USD("US dollar", 1.0),
    EUR("Euro", 1.08),
    GBP("Pound sterling", 1.27),
    AED("UAE dirham", 0.2723),
    CHF("Swiss franc", 1.11),
    CAD("Canadian dollar", 0.73),
    AUD("Australian dollar", 0.66),
    JPY("Japanese yen", 0.0067),
    ILS("Israeli shekel", 0.27);

    public static final Currency REFERENCE = USD;

    private final String label;
    private final double defaultUsdPerUnit;

    Currency(String label, double defaultUsdPerUnit) {
        this.label = label;
        this.defaultUsdPerUnit = defaultUsdPerUnit;
    }

    /** Code ISO (== {@link #name()}), ex. {@code "EUR"}. */
    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    /** Valeur d'un montant d'1 unité de cette devise en USD, par défaut (seed). */
    public double defaultUsdPerUnit() {
        return defaultUsdPerUnit;
    }

    /** Résout un code (insensible à la casse) vers une devise, {@link #USD} par défaut. */
    public static Currency fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return USD;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return USD;
        }
    }
}
