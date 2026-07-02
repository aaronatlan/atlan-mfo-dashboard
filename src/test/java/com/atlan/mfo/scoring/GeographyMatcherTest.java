package com.atlan.mfo.scoring;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeographyMatcherTest {

    private static final Set<String> FUND_PREFERRED = Set.of("US", "EUROPE", "UK", "DACH");
    private static final Set<String> DEAL_PREFERRED = Set.of("US", "EUROPE", "UK");

    @Test
    void normalizeCanonicalAndAliases() {
        assertEquals("US", GeographyMatcher.normalize("us"));
        assertEquals("US", GeographyMatcher.normalize("USA"));
        assertEquals("US", GeographyMatcher.normalize("United States"));
        assertEquals("UK", GeographyMatcher.normalize("united kingdom"));
        assertEquals("DACH", GeographyMatcher.normalize("Germany"));
        assertEquals("GLOBAL", GeographyMatcher.normalize("Worldwide"));
    }

    @Test
    void unknownBecomesOther() {
        assertEquals("OTHER", GeographyMatcher.normalize("Brazil"));
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
        assertTrue(GeographyMatcher.isMatch("GLOBAL", FUND_PREFERRED)); // GLOBAL toujours match
        assertFalse(GeographyMatcher.isMatch("OTHER", FUND_PREFERRED));
        assertTrue(GeographyMatcher.isMatch("UK", DEAL_PREFERRED));
        assertFalse(GeographyMatcher.isMatch("DACH", DEAL_PREFERRED)); // hors set grille C
    }
}
