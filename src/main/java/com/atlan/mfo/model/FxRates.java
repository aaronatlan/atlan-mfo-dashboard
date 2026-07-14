package com.atlan.mfo.model;

import com.atlan.mfo.model.enums.Currency;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Taux de change vers l'USD (devise de référence, §4) : pour chaque devise, la valeur
 * d'1 unité en dollars ({@code usdPerUnit}). Immuable ; construit à partir des valeurs
 * par défaut de {@link Currency}, éventuellement surchargées par la table {@code fx_rate}.
 */
public final class FxRates {

    private final Map<String, Double> usdPerUnit;

    private FxRates(Map<String, Double> usdPerUnit) {
        this.usdPerUnit = usdPerUnit;
    }

    /** Valeurs par défaut (seed) issues de l'enum {@link Currency}. */
    public static FxRates defaults() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (Currency c : Currency.values()) {
            m.put(c.code(), c.defaultUsdPerUnit());
        }
        return new FxRates(m);
    }

    /** Défauts surchargés par les taux persistés (code → usdPerUnit). */
    public static FxRates of(Map<String, Double> overrides) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (Currency c : Currency.values()) {
            m.put(c.code(), c.defaultUsdPerUnit());
        }
        if (overrides != null) {
            overrides.forEach((code, rate) -> {
                if (code != null && rate != null && rate > 0) {
                    m.put(code.trim().toUpperCase(java.util.Locale.ROOT), rate);
                }
            });
        }
        return new FxRates(m);
    }

    /** Taux (USD par unité) d'une devise ; 1.0 si inconnue (traitée comme USD). */
    public double usdPerUnit(String currencyCode) {
        Double r = usdPerUnit.get(Currency.fromCode(currencyCode).code());
        return r == null ? 1.0 : r;
    }

    /** Convertit un montant natif en USD, ou {@code null} si le montant est {@code null}. */
    public Double toUsd(Double amount, String currencyCode) {
        return amount == null ? null : amount * usdPerUnit(currencyCode);
    }

    /** Vue immuable des taux courants (code → usdPerUnit), pour l'éditeur. */
    public Map<String, Double> asMap() {
        return new LinkedHashMap<>(usdPerUnit);
    }
}
