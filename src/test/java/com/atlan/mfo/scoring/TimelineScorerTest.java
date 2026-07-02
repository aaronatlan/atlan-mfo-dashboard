package com.atlan.mfo.scoring;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineScorerTest {

    private static final LocalDate REF = LocalDate.of(2026, 1, 1);
    private static final int[] DAYS = {30, 60, 90};
    private static final double[] FRAC = {1.0, 0.6, 0.3};

    @Test
    void ladder() {
        assertEquals(1.0, frac(REF.plusDays(20)), 1e-9);   // ≤30j
        assertEquals(0.6, frac(REF.plusDays(45)), 1e-9);   // ≤60j
        assertEquals(0.3, frac(REF.plusDays(75)), 1e-9);   // ≤90j
        assertEquals(0.0, frac(REF.plusDays(120)), 1e-9);  // au-delà
    }

    @Test
    void pastDateScoresZero() {
        assertEquals(0.0, frac(REF.minusDays(5)), 1e-9);
    }

    @Test
    void nullDateExcluded() {
        assertTrue(TimelineScorer.fraction(null, REF, DAYS, FRAC).isEmpty());
    }

    private double frac(LocalDate target) {
        return TimelineScorer.fraction(target, REF, DAYS, FRAC).getAsDouble();
    }
}
