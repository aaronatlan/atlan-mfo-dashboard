package com.atlan.mfo.ui.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Formatage d'affichage des valeurs (suffixes x, %, m, bn). La saisie brute est
 * stockée en fraction décimale / nombre nu ; le formatage est purement UI (§4).
 */
public final class Formatters {

    private static final String DASH = "—";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Formatters() {
    }

    /** Montant → suffixe k / m / bn (sans devise). */
    public static String money(Double value) {
        if (value == null) {
            return DASH;
        }
        double v = value;
        double abs = Math.abs(v);
        if (abs >= 1_000_000_000d) {
            return trim(v / 1_000_000_000d) + "bn";
        }
        if (abs >= 1_000_000d) {
            return trim(v / 1_000_000d) + "m";
        }
        if (abs >= 1_000d) {
            return trim(v / 1_000d) + "k";
        }
        return trim(v);
    }

    /**
     * Montant préfixé du code devise (ex. « EUR 12m », « USD 154m »). Les agrégats
     * globaux passent {@code "USD"} (devise de référence, §4) ; les opportunités leur
     * devise native.
     */
    public static String money(Double value, String currencyCode) {
        if (value == null) {
            return DASH;
        }
        String code = (currencyCode == null || currencyCode.isBlank()) ? "USD" : currencyCode.trim();
        return code + " " + money(value);
    }

    /** Fraction décimale → pourcentage (0.137 → « 13.7% »). */
    public static String percent(Double fraction) {
        return fraction == null ? DASH : trim(fraction * 100d) + "%";
    }

    /** Multiple nu → suffixe x (1.4 → « 1.40x »). */
    public static String multiple(Double value) {
        return value == null ? DASH : String.format("%.2fx", value);
    }

    public static String score(Integer score) {
        return score == null ? DASH : Integer.toString(score);
    }

    public static String date(LocalDate date) {
        return date == null ? DASH : DATE.format(date);
    }

    public static String text(String s) {
        return (s == null || s.isBlank()) ? DASH : s;
    }

    /** Enlève les décimales inutiles (25.0 → « 25 », 1.25 → « 1.3 »). */
    private static String trim(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return String.format("%.1f", v);
    }
}
