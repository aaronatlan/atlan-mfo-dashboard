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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Attendus calculés à la main depuis la méthodologie, jamais recopiés depuis la sortie
 * du moteur — un test qui enregistre ce que le code produit ne prouve rien.
 *
 * <p>Avec {@code targetAttainment = 0,8}, k = ln 5, donc la courbe se lit simplement :
 * {@code sous-score = points × (1 − 5^(−valeur ÷ cible))}.
 *
 * <p>La géographie n'entre plus dans le score : les points DPI/TVPI/IRR des grilles
 * fonds ont été redistribués ×1,2 (36/24/30, total 90 inchangé) et ceux de la grille
 * deals ×1,125 (28,125/22,5/11,25/28,125, total 90 inchangé).
 */
class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine();
    private static final LocalDate REF = LocalDate.of(2026, 1, 1);

    /* ---------- Grilles ---------- */

    @Test
    void gridA_singleVintage() {
        // Millésime 2021 → âge 5 ; maturité = (5−3)/5 = 0,4 → DPI 14,4 pts, TVPI 45,6 pts.
        // DPI  0,65/0,8  → (1−5^−0,8125) × 14,4 = 10,51
        // TVPI 2,1/2,5   → (1−5^−0,84)   × 45,6 = 33,80   (TVPI absent → repli sur MOIC)
        // IRR  0,24/0,30 → (1−5^−0,8)    × 30   = 21,72
        // earned 66,03 / possible 90 → 73,4 → 73
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, null, "US", List.of(v(2021, 0.65, 0.24, 2.1)));
        ScoreBreakdown b = engine.score(f, REF);
        assertEquals(73, b.score());
        assertEquals(Tier.STRONG, b.tier());
    }

    @Test
    void gridA_twoVintages_recencyWeighted() {
        // 2022 (poids 1) + 2018 (poids 0,5) : DPI 0,567 ; IRR 0,24 ; MOIC 2,0
        // âge pondéré = (4 + 0,5×8)/1,5 = 5,33 → maturité 0,467 → DPI 16,8 pts, TVPI 43,2 pts
        // earned 64,43 / possible 90 → 71,6 → 72
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, null, "US",
                List.of(v(2022, 0.30, 0.26, 1.9), v(2018, 1.10, 0.20, 2.2)));
        assertEquals(72, engine.score(f, REF).score());
    }

    @Test
    void gridB_privateCredit() {
        // Cibles crédit : DPI 0,7 / TVPI 1,8 / IRR 0,20 ; maturité 0,4 → 14,4 et 45,6 pts
        // earned 63,17 / possible 90 → 70,2 → 70
        FundInvestment f = fund(Category.PRIVATE_CREDIT, "PRIVATE_CREDIT", "US",
                List.of(v(2021, 0.55, 0.14, 1.4)));
        assertEquals(70, engine.score(f, REF).score());
    }

    @Test
    void gridC_deal() {
        // CAGR (1−5^−1,175) × 28,125 ; marge (1−5^−0,7143) × 22,5
        // FCF (1−5^−0,7778) × 11,25 ; IRR (1−5^−1,0667) × 28,125
        // earned 70,36 / possible 90 → 78,2 → 78
        DirectDeal d = deal("US", 0.47, 0.25, 0.70, 0.32);
        assertEquals(78, engine.score(d, REF).score());
    }

    @Test
    void gridSelectedByAssetClass_notByLegacyCategory() {
        // Mêmes chiffres, classes différentes → cibles différentes → scores différents.
        List<FundVintage> vs = List.of(v(2018, 0.9, 0.15, 1.8));
        int pe = engine.score(fund(Category.BUYOUT_GROWTH_VC, "PRIVATE_EQUITY", "US", vs), REF).score();
        int realAssets = engine.score(fund(Category.BUYOUT_GROWTH_VC, "REAL_ASSETS", "US", vs), REF).score();
        // Real assets vise IRR 12 % et TVPI 1,8x : le même fonds y est jugé bien meilleur
        // qu'à l'aune du private equity (IRR 30 %, TVPI 2,5x).
        assertTrue(realAssets > pe, "real assets=" + realAssets + " devrait dépasser pe=" + pe);
    }

    /* ---------- Courbe : le défaut que la refonte corrige ---------- */

    @Test
    void curveSeparatesEliteFromMerelyOnTarget() {
        // Millésime 2018 → âge 8 → maturité 1 → DPI 36 pts, TVPI 24 pts.
        // Pile à la cible : chaque composant vaut 80 % → 28,8 + 19,2 + 24 = 72 / 90 → 80
        FundInvestment onTarget = fund(Category.BUYOUT_GROWTH_VC, "PRIVATE_EQUITY", "US",
                List.of(v(2018, 0.8, 0.30, 2.5)));
        // Trois fois la cible : 35,71 + 23,50 + 28,80 = 88,01 / 90 → 97,8 → plafond 95
        FundInvestment elite = fund(Category.BUYOUT_GROWTH_VC, "PRIVATE_EQUITY", "US",
                List.of(v(2018, 2.4, 0.60, 6.0)));

        assertEquals(80, engine.score(onTarget, REF).score());
        assertEquals(95, engine.score(elite, REF).score());
        // L'ancienne grille (plafond dur à la cible) les donnait tous deux à 95.
    }

    /* ---------- Maturité : la courbe en J ---------- */

    @Test
    void youngTrackRecord_dpiCarriesNoWeight_tvpiCarriesAll() {
        // Millésime 2024 → âge 2 < 3 → maturité 0 : le DPI ne pèse rien et n'est pas affiché.
        FundInvestment young = fund(Category.BUYOUT_GROWTH_VC, "VENTURE_CAPITAL", "US",
                List.of(vt(2024, 0.05, 1.35, 0.12, 1.30)));
        ScoreBreakdown b = engine.score(young, REF);

        assertFalse(has(b, "DPI"), "le DPI ne doit pas être affiché sous le seuil de maturité");
        assertEquals(60.0, max(b, "TVPI"), 1e-9);   // 36 + 24 : la dimension multiple entière
    }

    @Test
    void matureTrackRecord_dpiCarriesFullWeight() {
        FundInvestment mature = fund(Category.BUYOUT_GROWTH_VC, "PRIVATE_EQUITY", "US",
                List.of(v(2018, 0.9, 0.20, 2.0)));
        ScoreBreakdown b = engine.score(mature, REF);
        assertEquals(36.0, max(b, "DPI"), 1e-9);
        assertEquals(24.0, max(b, "TVPI"), 1e-9);
    }

    @Test
    void tvpiPreferredOverMoic_whenBothReported() {
        // TVPI 1,0 (médiocre) vs MOIC 10,0 (mirobolant) : c'est la TVPI qui doit primer.
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, "PRIVATE_EQUITY", "US",
                List.of(vt(2018, 0.9, 1.0, 0.20, 10.0)));
        // TVPI 1,0/2,5 = 0,4 → (1−5^−0,4) × 24 = 11,39 — très loin des 24 qu'aurait donné le MOIC
        assertEquals(11.39, sub(engine.score(f, REF), "TVPI"), 0.01);
    }

    /* ---------- Cas limites ---------- */

    @Test
    void missingMetricsExcluded_andFloorApplies() {
        // Seul le DPI (à la cible) est communiqué : maturité 0,4 → 14,4 pts × 80 % = 11,52
        // earned 11,52, possible 14,4, mais plancher 80 → 11,52/80×100 = 14,4 → 14
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, null, null, List.of(v(2021, 0.8, null, null)));
        ScoreBreakdown b = engine.score(f, REF);
        assertEquals(14, b.score());
        assertEquals(Tier.CAUTION, b.tier());
        assertFalse(communicated(b, "TVPI"));
        assertFalse(communicated(b, "IRR"));
        assertFalse(has(b, "Geography"), "la géographie ne doit plus faire partie des composants notés");
    }

    @Test
    void scoreIsCappedAt95() {
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, "PRIVATE_EQUITY", "US",
                List.of(v(2021, 5.0, 1.0, 10.0)));
        assertEquals(95, engine.score(f, REF).score());
    }

    @Test
    void negativeMetricScoresZeroNotNegative() {
        // IRR négatif : sous-score 0, jamais négatif, et la métrique reste communiquée.
        // 10,51 (DPI) + 33,80 (TVPI) + 0 (IRR) = 44,31 / 90 → 49,2 → 49
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, null, "US", List.of(v(2021, 0.65, -0.10, 2.1)));
        ScoreBreakdown b = engine.score(f, REF);
        assertEquals(0.0, sub(b, "IRR"), 1e-9);
        assertTrue(communicated(b, "IRR"));
        assertEquals(49, b.score());
    }

    @Test
    void noDataScoresZeroCaution() {
        FundInvestment f = fund(Category.BUYOUT_GROWTH_VC, null, null, List.of(v(2021, null, null, null)));
        ScoreBreakdown b = engine.score(f, REF);
        assertEquals(0, b.score());
        assertEquals(Tier.CAUTION, b.tier());
    }

    @Test
    void geographyIsNotScored() {
        // La géographie reste une donnée du fonds (affichée, filtrable, cartographiée en
        // présentation) mais n'apparaît plus du tout parmi les composants du score : deux
        // fonds identiques sauf la géographie doivent produire exactement le même score.
        List<FundVintage> vs = List.of(v(2021, 0.65, 0.24, 2.1));
        FundInvestment us = fund(Category.BUYOUT_GROWTH_VC, null, "US", vs);
        FundInvestment brazil = fund(Category.BUYOUT_GROWTH_VC, null, "Brazil", vs);
        FundInvestment blank = fund(Category.BUYOUT_GROWTH_VC, null, null, vs);

        assertFalse(has(engine.score(us, REF), "Geography"));
        assertEquals(engine.score(us, REF).score(), engine.score(brazil, REF).score());
        assertEquals(engine.score(us, REF).score(), engine.score(blank, REF).score());
    }

    @Test
    void scoreIsStationary_deadlineDoesNotMoveIt() {
        // La timeline est sortie du score : deux dates de closing radicalement différentes
        // doivent donner exactement le même score, sur des données identiques.
        List<FundVintage> vs = List.of(v(2021, 0.65, 0.24, 2.1));
        FundInvestment soon = fund(Category.BUYOUT_GROWTH_VC, null, "US", vs, REF.plusDays(20));
        FundInvestment later = fund(Category.BUYOUT_GROWTH_VC, null, "US", vs, REF.plusDays(400));
        assertEquals(engine.score(soon, REF).score(), engine.score(later, REF).score());
    }

    /* ---------- Helpers ---------- */

    private static double sub(ScoreBreakdown b, String label) {
        return b.components().stream().filter(c -> c.label().equals(label))
                .findFirst().orElseThrow().subScore();
    }

    private static double max(ScoreBreakdown b, String label) {
        return b.components().stream().filter(c -> c.label().equals(label))
                .findFirst().orElseThrow().maxPoints();
    }

    private static boolean communicated(ScoreBreakdown b, String label) {
        return b.components().stream().filter(c -> c.label().equals(label))
                .findFirst().orElseThrow().communicated();
    }

    private static boolean has(ScoreBreakdown b, String label) {
        return b.components().stream().anyMatch(c -> c.label().equals(label));
    }

    /** Millésime sans TVPI (repli sur MOIC). */
    private static FundVintage v(int year, Double dpi, Double irr, Double moic) {
        return new FundVintage(1, 1, year, dpi, null, irr, moic);
    }

    /** Millésime avec TVPI explicite. */
    private static FundVintage vt(int year, Double dpi, Double tvpi, Double irr, Double moic) {
        return new FundVintage(1, 1, year, dpi, tvpi, irr, moic);
    }

    private static FundInvestment fund(Category cat, String assetClass, String geo, List<FundVintage> vintages) {
        return fund(cat, assetClass, geo, vintages, null);
    }

    private static FundInvestment fund(Category cat, String assetClass, String geo,
                                       List<FundVintage> vintages, LocalDate finalClose) {
        return new FundInvestment(1, cat, "Test", null, DealStatus.INITIAL_REVIEW, null, geo, assetClass, null,
                vintages, null, finalClose, null, null, null, null, null, null, null, 0, null, null,
                null, null, null, "USD",
                null, null, null, null);
    }

    private static DirectDeal deal(String geo, Double cagr, Double margin, Double fcfConv, Double expIrr) {
        return new DirectDeal(1, "Deal", null, DealStatus.INITIAL_REVIEW, null, null, null, geo, null, null,
                null, cagr, null, null, margin, null, fcfConv, null,
                null, null, null, expIrr, null,
                null, null, null,
                null, null, null, null, null, null, null,
                0, null, null,
                null, null, null, "USD",
                null, null, null, null, null);
    }
}
