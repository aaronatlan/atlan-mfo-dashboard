package com.atlan.mfo.scoring;

import com.atlan.mfo.model.enums.Category;

import java.util.Set;

/**
 * Poids, cibles et paramètres des trois grilles de scoring (voir §5). Centralisé
 * ici pour pouvoir ajuster la méthodologie sans toucher au moteur.
 */
public final class ScoringProfile {

    /**
     * Métrique en ratio : {@code sous-score = CLAMP(valeur / cible, 0, 1) × points}.
     * Plancher à 0 : une métrique négative (ex. IRR d'un fonds perdant) vaut 0 point
     * mais ne retranche jamais de points (§5.1).
     */
    public record Ratio(double points, double target) {
        public double subScore(double value) {
            return Math.max(0.0, Math.min(value / target, 1.0)) * points;
        }
    }

    /** Géographie : plein score si région préférée (ou GLOBAL), sinon « autre ». */
    public record Geo(double matchPoints, double otherPoints, Set<String> preferred) {
    }

    /** Grille fonds (A et B). */
    public record FundGrid(Ratio dpi, Ratio irr, Ratio moic, Geo geo, double timelinePoints) {
    }

    /** Grille deals directs (C). */
    public record DealGrid(Ratio cagr, Ratio margin, Ratio fcf, Ratio irr, Geo geo, double timelinePoints) {
    }

    // Normalisation (§5.1)
    public final double possibleFloor;
    public final double scoreCap;

    // Agrégation multi-millésimes (§5.5)
    public final double vintageHalfLife;

    // Timeline (§5.2–5.4) : fraction des points selon la proximité en jours
    public final int[] timelineDays = {30, 60, 90};
    public final double[] timelineFraction = {1.0, 0.6, 0.3};

    public final FundGrid gridA;
    public final FundGrid gridB;
    public final DealGrid gridC;

    private ScoringProfile(double possibleFloor, double scoreCap, double vintageHalfLife,
                           FundGrid gridA, FundGrid gridB, DealGrid gridC) {
        this.possibleFloor = possibleFloor;
        this.scoreCap = scoreCap;
        this.vintageHalfLife = vintageHalfLife;
        this.gridA = gridA;
        this.gridB = gridB;
        this.gridC = gridC;
    }

    /** Grille appliquée à un fonds : B pour le private credit, A sinon (§5). */
    public FundGrid fundGrid(boolean privateCredit) {
        return privateCredit ? gridB : gridA;
    }

    /** Configuration par défaut (méthodologie §5). */
    public static ScoringProfile defaults() {
        Set<String> fundPreferred = Set.of("US", "EUROPE", "UK");
        Set<String> dealPreferred = Set.of("US", "EUROPE", "UK");

        FundGrid gridA = new FundGrid(
                new Ratio(30, 0.8),
                new Ratio(25, 0.3),
                new Ratio(20, 2.5),
                new Geo(15, 8, fundPreferred),
                10);

        FundGrid gridB = new FundGrid(
                new Ratio(30, 0.7),
                new Ratio(25, 0.2),
                new Ratio(20, 1.8),
                new Geo(15, 8, fundPreferred),
                10);

        DealGrid gridC = new DealGrid(
                new Ratio(25, 0.4),
                new Ratio(20, 0.35),
                new Ratio(10, 0.9),
                new Ratio(25, 0.3),
                new Geo(10, 5, dealPreferred),
                10);

        return new ScoringProfile(80, 95, 4, gridA, gridB, gridC);
    }

    /* ---- Sérialisation clé→valeur (méthodologie éditable, §5) ---- */

    /** Représentation plate des paramètres numériques (points, cibles, global). */
    public java.util.Map<String, Double> toMap() {
        var m = new java.util.LinkedHashMap<String, Double>();
        putFundGrid(m, "gridA", gridA);
        putFundGrid(m, "gridB", gridB);
        m.put("gridC.cagr.points", gridC.cagr().points());
        m.put("gridC.cagr.target", gridC.cagr().target());
        m.put("gridC.margin.points", gridC.margin().points());
        m.put("gridC.margin.target", gridC.margin().target());
        m.put("gridC.fcf.points", gridC.fcf().points());
        m.put("gridC.fcf.target", gridC.fcf().target());
        m.put("gridC.irr.points", gridC.irr().points());
        m.put("gridC.irr.target", gridC.irr().target());
        m.put("gridC.geo.points", gridC.geo().matchPoints());
        m.put("gridC.geo.other", gridC.geo().otherPoints());
        m.put("gridC.timeline.points", gridC.timelinePoints());
        m.put("global.possibleFloor", possibleFloor);
        m.put("global.scoreCap", scoreCap);
        m.put("global.vintageHalfLife", vintageHalfLife);
        return m;
    }

    private static void putFundGrid(java.util.Map<String, Double> m, String p, FundGrid g) {
        m.put(p + ".dpi.points", g.dpi().points());
        m.put(p + ".dpi.target", g.dpi().target());
        m.put(p + ".irr.points", g.irr().points());
        m.put(p + ".irr.target", g.irr().target());
        m.put(p + ".moic.points", g.moic().points());
        m.put(p + ".moic.target", g.moic().target());
        m.put(p + ".geo.points", g.geo().matchPoints());
        m.put(p + ".geo.other", g.geo().otherPoints());
        m.put(p + ".timeline.points", g.timelinePoints());
    }

    /**
     * Construit un profil depuis des surcharges clé→valeur ; toute clé absente prend
     * sa valeur par défaut. Les ensembles géographiques et paliers de timeline (en
     * jours) restent fixes (non éditables).
     */
    public static ScoringProfile fromMap(java.util.Map<String, Double> overrides) {
        var d = defaults().toMap();
        java.util.function.Function<String, Double> g =
                k -> overrides.getOrDefault(k, d.get(k));
        Set<String> fundPref = Set.of("US", "EUROPE", "UK");
        Set<String> dealPref = Set.of("US", "EUROPE", "UK");

        FundGrid a = fundGridFrom(g, "gridA", fundPref);
        FundGrid b = fundGridFrom(g, "gridB", fundPref);
        DealGrid c = new DealGrid(
                new Ratio(g.apply("gridC.cagr.points"), g.apply("gridC.cagr.target")),
                new Ratio(g.apply("gridC.margin.points"), g.apply("gridC.margin.target")),
                new Ratio(g.apply("gridC.fcf.points"), g.apply("gridC.fcf.target")),
                new Ratio(g.apply("gridC.irr.points"), g.apply("gridC.irr.target")),
                new Geo(g.apply("gridC.geo.points"), g.apply("gridC.geo.other"), dealPref),
                g.apply("gridC.timeline.points"));

        return new ScoringProfile(
                g.apply("global.possibleFloor"), g.apply("global.scoreCap"),
                g.apply("global.vintageHalfLife"), a, b, c);
    }

    private static FundGrid fundGridFrom(java.util.function.Function<String, Double> g,
                                         String p, Set<String> pref) {
        return new FundGrid(
                new Ratio(g.apply(p + ".dpi.points"), g.apply(p + ".dpi.target")),
                new Ratio(g.apply(p + ".irr.points"), g.apply(p + ".irr.target")),
                new Ratio(g.apply(p + ".moic.points"), g.apply(p + ".moic.target")),
                new Geo(g.apply(p + ".geo.points"), g.apply(p + ".geo.other"), pref),
                g.apply(p + ".timeline.points"));
    }
}
