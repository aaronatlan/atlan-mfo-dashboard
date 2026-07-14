package com.atlan.mfo.scoring;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Normalisation de la géographie vers un vocabulaire canonique, pour éviter les
 * non-match silencieux (« USA » vs « US ») — voir §13.1.
 *
 * <p>Tokens canoniques : {@code US, EUROPE, UK, GLOBAL, OTHER}.
 */
public final class GeographyMatcher {

    public static final Set<String> CANONICAL = Set.of("US", "EUROPE", "UK", "GLOBAL", "OTHER");

    private static final Map<String, String> ALIASES = buildAliases();

    private GeographyMatcher() {
    }

    /**
     * Renvoie le token canonique, ou {@code null} si l'entrée est vide (= non
     * communiquée, exclue du scoring). Toute valeur inconnue non vide → OTHER.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (CANONICAL.contains(key)) {
            return key;
        }
        return ALIASES.getOrDefault(key, "OTHER");
    }

    /** Un token compte comme match s'il est dans le set préféré ou s'il est GLOBAL. */
    public static boolean isMatch(String canonical, Set<String> preferred) {
        return "GLOBAL".equals(canonical) || preferred.contains(canonical);
    }

    private static Map<String, String> buildAliases() {
        return Map.ofEntries(
                Map.entry("USA", "US"),
                Map.entry("U.S.", "US"),
                Map.entry("U.S.A.", "US"),
                Map.entry("UNITED STATES", "US"),
                Map.entry("ETATS-UNIS", "US"),
                Map.entry("ÉTATS-UNIS", "US"),
                Map.entry("EU", "EUROPE"),
                Map.entry("EUROZONE", "EUROPE"),
                Map.entry("EURO", "EUROPE"),
                Map.entry("GB", "UK"),
                Map.entry("GREAT BRITAIN", "UK"),
                Map.entry("UNITED KINGDOM", "UK"),
                Map.entry("ENGLAND", "UK"),
                Map.entry("BRITAIN", "UK"),
                Map.entry("GERMANY", "EUROPE"),
                Map.entry("ALLEMAGNE", "EUROPE"),
                Map.entry("DEUTSCHLAND", "EUROPE"),
                Map.entry("AUSTRIA", "EUROPE"),
                Map.entry("AUTRICHE", "EUROPE"),
                Map.entry("SWITZERLAND", "EUROPE"),
                Map.entry("SUISSE", "EUROPE"),
                Map.entry("D-A-CH", "EUROPE"),
                Map.entry("WORLD", "GLOBAL"),
                Map.entry("WORLDWIDE", "GLOBAL"),
                Map.entry("MONDE", "GLOBAL"),
                Map.entry("INTERNATIONAL", "GLOBAL"));
    }
}
