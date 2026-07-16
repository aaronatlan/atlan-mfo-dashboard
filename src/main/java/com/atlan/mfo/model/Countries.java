package com.atlan.mfo.model;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Référentiel des pays proposables — source unique, alignée sur l'asset carte
 * Natural Earth, partagée par la saisie ({@code FormControls}) et la carte de chaleur
 * ({@code WorldHeatMap}).
 *
 * <p>La géographie d'une opportunité est un <b>pays</b>. Elle n'entre pas dans le score
 * (voir {@code ScoringProfile}) : c'est une donnée affichée, filtrable et cartographiée.
 */
public final class Countries {

    private Countries() {
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
