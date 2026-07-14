package com.atlan.mfo.model;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Référentiel des pays (source unique, alignée sur l'asset carte Natural Earth) et
 * dérivation de la <b>région</b> d'un pays pour le scoring géographique (§13.1).
 *
 * <p>La géographie d'une opportunité est désormais un <b>pays</b> ; le moteur en dérive
 * une région préférée (US / EUROPE / UK) ou {@code OTHER} (communiqué mais non préféré).
 * Il n'y a plus de token « GLOBAL ».
 */
public final class Countries {

    private Countries() {
    }

    private static final String USA = "United States of America";
    private static final String GBR = "United Kingdom";

    /** Marchés européens « préférés » (aligné sur la carte ; le reste → OTHER). */
    private static final Set<String> EUROPE = Set.of(
            "Germany", "Austria", "Switzerland", "France", "Italy", "Spain", "Netherlands", "Belgium",
            "Sweden", "Norway", "Denmark", "Finland", "Poland", "Ireland", "Portugal", "Czechia",
            "Greece", "Hungary", "Romania", "Bulgaria", "Croatia", "Slovakia", "Slovenia", "Lithuania",
            "Latvia", "Estonia", "Luxembourg", "Iceland");

    /**
     * Région de scoring d'une géographie (pays ou token hérité), ou {@code null} si
     * l'entrée est vide (= non communiquée, exclue du scoring). Toute valeur non vide
     * et non préférée → {@code OTHER}.
     */
    public static String regionOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        switch (s.toUpperCase(Locale.ROOT)) {
            case "US", "USA", "U.S.", "U.S.A.", "UNITED STATES", "UNITED STATES OF AMERICA",
                 "ETATS-UNIS", "ÉTATS-UNIS" -> {
                return "US";
            }
            case "UK", "GB", "GREAT BRITAIN", "UNITED KINGDOM", "ENGLAND", "BRITAIN" -> {
                return "UK";
            }
            case "EUROPE", "EU", "EUROZONE", "EURO" -> {
                return "EUROPE";
            }
            default -> {
                if (EUROPE.contains(s)) {
                    return "EUROPE";
                }
                return "OTHER";
            }
        }
    }

    private static volatile List<String> cachedNames;

    /** Liste triée de tous les pays proposables (asset carte Natural Earth). */
    public static List<String> names() {
        List<String> cached = cachedNames;
        if (cached != null) {
            return cached;
        }
        List<String> names = new ArrayList<>();
        try (InputStream in = Countries.class.getResourceAsStream("/map/world-natural-earth.tsv")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int tab = line.indexOf('\t');
                    if (tab > 0) {
                        names.add(line.substring(0, tab));
                    }
                }
            }
        } catch (Exception ignored) {
            // Asset absent : la liste sera vide (le formulaire acceptera la saisie libre).
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        cachedNames = List.copyOf(names);
        return cachedNames;
    }
}
