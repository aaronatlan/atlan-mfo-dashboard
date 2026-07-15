package com.atlan.mfo.scoring;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UrgencyTest {

    private static final LocalDate REF = LocalDate.of(2026, 1, 1);

    @Test
    void noDeadline_noLabel() {
        assertNull(Urgency.label(null, REF));
    }

    @Test
    void futureDeadline_countsDown() {
        assertEquals("20 days left", Urgency.label(REF.plusDays(20), REF));
        assertEquals("400 days left", Urgency.label(REF.plusDays(400), REF));
    }

    @Test
    void singularAndToday() {
        assertEquals("1 day left", Urgency.label(REF.plusDays(1), REF));
        assertEquals("Due today", Urgency.label(REF, REF));
    }

    @Test
    void pastDeadline() {
        assertEquals("Passed", Urgency.label(REF.minusDays(1), REF));
    }
}
