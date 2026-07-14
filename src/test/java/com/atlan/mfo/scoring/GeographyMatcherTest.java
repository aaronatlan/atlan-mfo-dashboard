package com.atlan.mfo.scoring;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeographyMatcherTest {

    private static final Set<String> FUND_PREFERRED = Set.of("US", "EUROPE", "UK");
    private static final Set<String> DEAL_PREFERRED = Set.of("US", "EUROPE", "UK");

    @Test
    void countryToRegion() {
        assertEquals("US", GeographyMatcher.normalize("United States of America"));
        assertEquals("US", GeographyMatcher.normalize("USA"));               // token hérité
        assertEquals("UK", GeographyMatcher.normalize("United Kingdom"));
        assertEquals("EUROPE", GeographyMatcher.normalize("Germany"));
        assertEquals("EUROPE", GeographyMatcher.normalize("France"));
    }

    @Test
    void nonPreferredCountryBecomesOther() {
        assertEquals("OTHER", GeographyMatcher.normalize("Brazil"));
        assertEquals("OTHER", GeographyMatcher.normalize("Japan"));
    }

    @Test
    void blankIsNull() {
        assertNull(GeographyMatcher.normalize(""));
        assertNull(GeographyMatcher.normalize("   "));
        assertNull(GeographyMatcher.normalize(null));
    }

    @Test
    void matchRules() {
        assertTrue(GeographyMatcher.isMatch("US", FUND_PREFERRED));
        assertTrue(GeographyMatcher.isMatch("EUROPE", FUND_PREFERRED));
        assertFalse(GeographyMatcher.isMatch("OTHER", FUND_PREFERRED));
        assertFalse(GeographyMatcher.isMatch(null, FUND_PREFERRED));
        assertTrue(GeographyMatcher.isMatch("UK", DEAL_PREFERRED));
        assertFalse(GeographyMatcher.isMatch("OTHER", DEAL_PREFERRED)); // hors set préféré
    }
}
