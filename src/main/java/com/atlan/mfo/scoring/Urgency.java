package com.atlan.mfo.scoring;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Urgence d'une opportunité : proximité de sa date d'échéance (final close d'un fonds,
 * deadline d'un deal).
 *
 * <p>Délibérément <b>hors du score</b>. Une échéance est un fait de calendrier, pas une
 * mesure de qualité : la mélanger au score le ferait dériver tout seul avec le temps
 * (le même dossier vaudrait moins le mois suivant, sans information nouvelle) et
 * gonflerait le tier d'un dossier médiocre mais pressé. Le comité a besoin de l'info —
 * elle est donc affichée, à côté du score et non dedans.
 */
public final class Urgency {

    private Urgency() {
    }

    /** Libellé court pour l'affichage, ou {@code null} si aucune échéance n'est renseignée. */
    public static String label(LocalDate target, LocalDate reference) {
        if (target == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(reference, target);
        if (days < 0) {
            return "Passed";
        }
        if (days == 0) {
            return "Due today";
        }
        if (days == 1) {
            return "1 day left";
        }
        return days + " days left";
    }
}
