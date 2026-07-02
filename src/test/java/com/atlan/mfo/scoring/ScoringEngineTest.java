package com.atlan.mfo.scoring;

import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.model.enums.Tier;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine();
    private static final LocalDate REF = LocalDate.of(2026, 1, 1);

    /* ---------- Exemples de la spec ---------- */

    @Test
    void gridA_singleVintage_matchesSpec58() {
        // §5.8 : DPI 0,65 / IRR 0,24 / MOIC 2,1 / géo US / pas de final close → 85
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, "US", null,
                List.of(v(2021, 0.65, 0.24, 2.1)));
        ScoreBreakdown b = engine.score(f, REF);
        assertEquals(85, b.score());
        assertEquals(Tier.STRONG, b.tier());
    }

    @Test
    void gridA_twoVintages_recencyWeighted_matchesSpec59() {
        // §5.9 : 2022 (récent) + 2018, pondération par récence (H=4), géo US → 80
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, "US", null,
                List.of(v(2022, 0.30, 0.26, 1.9), v(2018, 1.10, 0.20, 2.2)));
        ScoreBreakdown b = engine.score(f, REF);
        assertEquals(80, b.score());
        assertEquals(Tier.STRONG, b.tier());
    }

    @Test
    void gridB_privateCredit() {
        // DPI 0,55/0,7 + IRR 0,14/0,2 + MOIC 1,4/1,8 + géo US → 80
        FundInvestment f = fund(Category.PRIVATE_CREDIT, "US", null,
                List.of(v(2021, 0.55, 0.14, 1.4)));
        assertEquals(80, engine.score(f, REF).score());
    }

    @Test
    void gridC_deal() {
        // CAGR 0,47 + marge 0,25 + FCF 0,70 + IRR 0,32 + géo US → 91
        DirectDeal d = deal("US", null, 0.47, 0.25, 0.70, 0.32);
        assertEquals(91, engine.score(d, REF).score());
    }

    /* ---------- Cas limites ---------- */

    @Test
    void missingMetricsExcluded_andFloorApplies() {
        // Seul le DPI (à la cible) est communiqué → earned=30, possible=30,
        // mais plancher 80 : 30/80*100 = 37,5 → 38
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, null, null,
                List.of(v(2021, 0.8, null, null)));
        ScoreBreakdown b = engine.score(f, REF);
        assertEquals(38, b.score());
        assertEquals(Tier.CAUTION, b.tier());
        assertFalse(communicated(b, "IRR"));
        assertFalse(communicated(b, "MOIC"));
        assertFalse(communicated(b, "Géographie"));
        assertFalse(communicated(b, "Timeline"));
    }

    @Test
    void scoreIsCappedAt95() {
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, "US", null,
                List.of(v(2021, 5.0, 1.0, 10.0)));  // tout très au-dessus des cibles
        assertEquals(95, engine.score(f, REF).score());
    }

    @Test
    void noDataScoresZeroCaution() {
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, null, null,
                List.of(v(2021, null, null, null)));
        ScoreBreakdown b = engine.score(f, REF);
        assertEquals(0, b.score());
        assertEquals(Tier.CAUTION, b.tier());
    }

    @Test
    void geography_match_alias_other_excluded() {
        assertEquals(15.0, geoSub(fund(Category.BUYOUT_GROWTH_VC, "US", null, one())), 1e-9);
        assertEquals(15.0, geoSub(fund(Category.BUYOUT_GROWTH_VC, "USA", null, one())), 1e-9); // alias
        assertEquals(8.0, geoSub(fund(Category.BUYOUT_GROWTH_VC, "Brazil", null, one())), 1e-9); // autre
        assertFalse(communicated(engine.score(
                fund(Category.BUYOUT_GROWTH_VC, "  ", null, one()), REF), "Géographie")); // vide → exclu
    }

    @Test
    void timelineLadder_full_and_zeroWhenPast() {
        LocalDate soon = REF.plusDays(20);       // ≤30j → plein (10)
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, "US", soon, one());
        assertEquals(10.0, sub(engine.score(f, REF), "Timeline"), 1e-9);

        FundInvestment past = fund(Category.BUYOUT_GROWTH_VC, "US", REF.minusDays(10), one());
        assertEquals(0.0, sub(engine.score(past, REF), "Timeline"), 1e-9);
    }

    /* ---------- Helpers ---------- */

    private double geoSub(FundInvestment f) {
        return sub(engine.score(f, REF), "Géographie");
    }

    private static double sub(ScoreBreakdown b, String label) {
        return b.components().stream().filter(c -> c.label().equals(label))
                .findFirst().orElseThrow().subScore();
    }

    private static boolean communicated(ScoreBreakdown b, String label) {
        return b.components().stream().filter(c -> c.label().equals(label))
                .findFirst().orElseThrow().communicated();
    }

    private static List<FundVintage> one() {
        return List.of(v(2021, 0.65, 0.24, 2.1));
    }

    private static FundVintage v(int year, Double dpi, Double irr, Double moic) {
        return new FundVintage(1, 1, year, dpi, null, irr, moic);
    }

    private static FundInvestment fund(Category cat, String geo, LocalDate finalClose, List<FundVintage> vintages) {
        return new FundInvestment(1, cat, "Test", null, DealStatus.INITIAL_REVIEW, null, geo, null, null,
                vintages, null, finalClose, null, null, null, null, null, null, null, 0, null, null);
    }

    private static DirectDeal deal(String geo, LocalDate deadline, Double cagr, Double margin,
                                   Double fcfConv, Double expIrr) {
        return new DirectDeal(1, "Deal", null, DealStatus.INITIAL_REVIEW, null, null, null, geo, null, null,
                null, cagr, null, null, margin, null, fcfConv, null,
                null, null, null, expIrr, null,
                deadline, null, null,
                null, null, null, null, null, null, null,
                0, null, null);
    }
}
