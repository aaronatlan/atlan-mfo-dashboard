package com.atlan.mfo.scoring;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricParserTest {

    @Test
    void percent() {
        assertEquals(0.137, MetricParser.parse("13.7%").getAsDouble(), 1e-9);
    }

    @Test
    void plainDecimal() {
        assertEquals(0.30, MetricParser.parse("0.30").getAsDouble(), 1e-9);
    }

    @Test
    void multiple() {
        assertEquals(1.40, MetricParser.parse("1.40x").getAsDouble(), 1e-9);
    }

    @Test
    void decimalComma() {
        assertEquals(2.5, MetricParser.parse("2,5x").getAsDouble(), 1e-9);
    }

    @Test
    void moneySuffixes() {
        assertEquals(25_000_000d, MetricParser.parse("25m").getAsDouble(), 1e-9);
        assertEquals(1_200_000_000d, MetricParser.parse("1.2bn").getAsDouble(), 1e-9);
    }

    @Test
    void rangeExcluded() {
        assertTrue(MetricParser.parse("20-40x").isEmpty());
        assertTrue(MetricParser.parse("20–30%").isEmpty());
    }

    @Test
    void blankOrGarbageExcluded() {
        assertEquals(OptionalDouble.empty(), MetricParser.parse(""));
        assertEquals(OptionalDouble.empty(), MetricParser.parse(null));
        assertTrue(MetricParser.parse("abc").isEmpty());
    }
}
