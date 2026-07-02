package com.atlan.mfo.scoring;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.OptionalDouble;

/**
 * Sous-score de timeline : proximité d'une date cible (final close d'un fonds,
 * deadline d'un deal) par rapport à la date de référence (voir §5.2–5.4, §5.7).
 *
 * <p>Renvoie une <b>fraction</b> [0..1] des points ; le moteur la multiplie par
 * les points max de la grille. Une date absente → {@link OptionalDouble#empty()}
 * (composant exclu). Une date déjà passée → 0 (aucune proximité utile).
 */
public final class TimelineScorer {

    private TimelineScorer() {
    }

    public static OptionalDouble fraction(LocalDate target, LocalDate reference,
                                          int[] days, double[] fractions) {
        if (target == null) {
            return OptionalDouble.empty();
        }
        long d = ChronoUnit.DAYS.between(reference, target);
        if (d < 0) {
            return OptionalDouble.of(0d);
        }
        for (int i = 0; i < days.length; i++) {
            if (d <= days[i]) {
                return OptionalDouble.of(fractions[i]);
            }
        }
        return OptionalDouble.of(0d);
    }
}
