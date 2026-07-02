package com.atlan.mfo.scoring;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Normalise une saisie brute en valeur décimale pour le stockage / scoring
 * (voir §4, §5.7).
 *
 * <ul>
 *   <li>{@code "13.7%"} → 0.137 (fraction)</li>
 *   <li>{@code "1.40x"}, {@code "2,5x"} → 1.40 / 2.5 (multiple nu)</li>
 *   <li>{@code "25m"}, {@code "1.2bn"} → 25000000 / 1200000000</li>
 *   <li>{@code "0.30"} → 0.30</li>
 *   <li>fourchettes textuelles ({@code "20-40x"}) → exclues ({@link OptionalDouble#empty()})</li>
 * </ul>
 */
public final class MetricParser {

    private MetricParser() {
    }

    public static OptionalDouble parse(String raw) {
        if (raw == null) {
            return OptionalDouble.empty();
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return OptionalDouble.empty();
        }
        // Fourchette / texte : exclu du scoring automatique
        if (s.indexOf('–') >= 0 || s.indexOf('—') >= 0 || s.indexOf('/') >= 0
                || s.toLowerCase(Locale.ROOT).contains(" to ")
                || s.lastIndexOf('-') > 0) {
            return OptionalDouble.empty();
        }

        String t = s.toLowerCase(Locale.ROOT).replace(",", ".").replace(" ", "");
        double factor = 1.0;

        if (t.endsWith("%")) {
            factor = 0.01;
            t = strip(t, 1);
        } else if (t.endsWith("bn")) {
            factor = 1_000_000_000d;
            t = strip(t, 2);
        } else if (t.endsWith("x")) {
            t = strip(t, 1);
        } else if (t.endsWith("m")) {
            factor = 1_000_000d;
            t = strip(t, 1);
        } else if (t.endsWith("k")) {
            factor = 1_000d;
            t = strip(t, 1);
        }

        try {
            return OptionalDouble.of(Double.parseDouble(t) * factor);
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    private static String strip(String s, int suffixLen) {
        return s.substring(0, s.length() - suffixLen);
    }
}
