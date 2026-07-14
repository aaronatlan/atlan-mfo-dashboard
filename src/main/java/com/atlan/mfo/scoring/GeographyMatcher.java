package com.atlan.mfo.scoring;

import com.atlan.mfo.model.Countries;

import java.util.Set;

/**
 * Normalisation de la géographie (désormais un <b>pays</b>) vers une région de scoring,
 * pour éviter les non-match silencieux (voir §13.1). La table pays→région vit dans
 * {@link Countries} (source unique, partagée avec la carte et la saisie).
 *
 * <p>Régions de scoring : {@code US, EUROPE, UK} (préférées) et {@code OTHER}
 * (communiqué mais non préféré). Il n'y a plus de token {@code GLOBAL}.
 */
public final class GeographyMatcher {

    public static final Set<String> CANONICAL = Set.of("US", "EUROPE", "UK", "OTHER");

    private GeographyMatcher() {
    }

    /**
     * Région de scoring du pays fourni, ou {@code null} si l'entrée est vide (= non
     * communiquée, exclue du scoring). Tout pays non préféré → {@code OTHER}.
     */
    public static String normalize(String raw) {
        return Countries.regionOf(raw);
    }

    /** Un pays compte comme match si sa région est dans le set préféré. */
    public static boolean isMatch(String canonical, Set<String> preferred) {
        return canonical != null && preferred.contains(canonical);
    }
}
