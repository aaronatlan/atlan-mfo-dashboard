package com.atlan.mfo.scoring;

import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * Poids, cibles et paramètres des grilles de scoring (voir §5). Centralisé ici pour
 * pouvoir ajuster la méthodologie sans toucher au moteur.
 *
 * <p>Cinq grilles fonds — A (private equity), B (private credit), D (venture capital),
 * E (real assets), F (secondaires) — plus C (deals directs et co-investissements).
 * La grille appliquée découle de la classe d'actifs ({@link #fundGrid}).
 */
public final class ScoringProfile {

    /**
     * Métrique en ratio, à <b>rendement décroissant</b> :
     * {@code sous-score = points × (1 − e^(−k · valeur ÷ cible))}.
     *
     * <p>La cible est un repère, pas un plafond : l'atteindre vaut
     * {@link ScoringProfile#targetAttainment} des points, et un fonds qui la dépasse
     * continue d'en gagner, de moins en moins vite. Un plafond dur (l'ancien
     * {@code min(v/cible, 1)}) rendait indistinguables un fonds à la cible et un fonds
     * trois fois meilleur — rédhibitoire pour un outil dont le métier est de classer.
     *
     * <p>Plancher à 0 : une métrique négative (IRR d'un fonds perdant) vaut 0 point mais
     * ne retranche jamais (§5.1). Borné à {@code points} par construction de la courbe.
     */
    public record Ratio(double points, double target) {
        public double subScore(double value, double k) {
            if (value <= 0 || target <= 0) {
                return 0.0;
            }
            return points * (1 - Math.exp(-k * value / target));
        }
    }

    /** Géographie : plein score si région préférée, sinon « autre ». */
    public record Geo(double matchPoints, double otherPoints, Set<String> preferred) {
    }

    /**
     * Grille fonds. {@code dpi} = capital réellement distribué ; {@code tv} = valeur
     * totale (TVPI, à défaut MOIC).
     *
     * <p>Le moteur re-répartit les points de ces deux composants selon la maturité du
     * track record (voir {@code ScoringEngine.maturity}) : un fonds de millésime récent
     * n'a mécaniquement pas encore distribué (courbe en J), c'est donc sa valeur totale
     * qui porte le jugement. Les points affichés ici sont ceux <b>à pleine maturité</b> ;
     * leur somme ({@link #multiplePoints}) reste constante quelle que soit la maturité.
     */
    public record FundGrid(Ratio dpi, Ratio tv, Ratio irr, Geo geo) {
        public double multiplePoints() {
            return dpi.points() + tv.points();
        }
    }

    /** Grille deals directs (C). */
    public record DealGrid(Ratio cagr, Ratio margin, Ratio fcf, Ratio irr, Geo geo) {
    }

    // Normalisation (§5.1)
    public final double possibleFloor;
    public final double scoreCap;

    /** Fraction des points obtenue exactement à la cible (raideur de la courbe). */
    public final double targetAttainment;

    // Agrégation multi-millésimes (§5.5)
    public final double vintageHalfLife;

    // Maturité du track record, en années : le DPI ne pèse rien avant `maturityYoung`,
    // pleinement à partir de `maturityMature`, en glissement continu entre les deux.
    public final double maturityYoung;
    public final double maturityMature;

    public final FundGrid gridA;   // private equity
    public final FundGrid gridB;   // private credit
    public final FundGrid gridD;   // venture capital
    public final FundGrid gridE;   // real assets
    public final FundGrid gridF;   // secondaires
    public final DealGrid gridC;   // deals directs / co-investissements

    private ScoringProfile(double possibleFloor, double scoreCap, double targetAttainment,
                           double vintageHalfLife, double maturityYoung, double maturityMature,
                           FundGrid gridA, FundGrid gridB, FundGrid gridD, FundGrid gridE,
                           FundGrid gridF, DealGrid gridC) {
        this.possibleFloor = possibleFloor;
        this.scoreCap = scoreCap;
        this.targetAttainment = targetAttainment;
        this.vintageHalfLife = vintageHalfLife;
        this.maturityYoung = maturityYoung;
        this.maturityMature = maturityMature;
        this.gridA = gridA;
        this.gridB = gridB;
        this.gridD = gridD;
        this.gridE = gridE;
        this.gridF = gridF;
        this.gridC = gridC;
    }

    /** Raideur de la courbe, dérivée de {@link #targetAttainment} : k = −ln(1 − a). */
    public double curveK() {
        double a = Math.max(0.01, Math.min(targetAttainment, 0.99));
        return -Math.log(1 - a);
    }

    /** Grille appliquée à un fonds, selon sa classe d'actifs (§5, structure Patrimium). */
    public FundGrid fundGrid(String assetClass) {
        if (assetClass == null) {
            return gridA;
        }
        return switch (assetClass) {
            case "PRIVATE_CREDIT" -> gridB;
            case "VENTURE_CAPITAL" -> gridD;
            case "REAL_ASSETS" -> gridE;
            case "SECONDARIES" -> gridF;
            default -> gridA;   // PRIVATE_EQUITY et non classé
        };
    }

    /** Configuration par défaut (méthodologie §5). */
    public static ScoringProfile defaults() {
        Set<String> pref = Set.of("US", "EUROPE", "UK");
        Geo fundGeo = new Geo(15, 8, pref);

        // Grilles A et B : cibles historiques du comité, inchangées.
        FundGrid a = new FundGrid(new Ratio(30, 0.8), new Ratio(20, 2.5), new Ratio(25, 0.30), fundGeo);
        FundGrid b = new FundGrid(new Ratio(30, 0.7), new Ratio(20, 1.8), new Ratio(25, 0.20), fundGeo);

        // Grilles D, E, F : cibles À RATIFIER PAR LE COMITÉ D'INVESTISSEMENT.
        // Valeurs de départ calées sur les conventions publiques de quartiles par classe
        // (Cambridge Associates / Preqin) — points de départ de discussion, pas des
        // recommandations d'investissement. Éditables dans Methodology.
        FundGrid d = new FundGrid(new Ratio(30, 0.6), new Ratio(20, 3.0), new Ratio(25, 0.25), fundGeo);
        FundGrid e = new FundGrid(new Ratio(30, 0.9), new Ratio(20, 1.8), new Ratio(25, 0.12), fundGeo);
        FundGrid f = new FundGrid(new Ratio(30, 0.9), new Ratio(20, 1.7), new Ratio(25, 0.18), fundGeo);

        DealGrid c = new DealGrid(
                new Ratio(25, 0.4), new Ratio(20, 0.35), new Ratio(10, 0.9), new Ratio(25, 0.3),
                new Geo(10, 5, pref));

        return new ScoringProfile(80, 95, 0.80, 4, 3, 8, a, b, d, e, f, c);
    }

    /* ---- Sérialisation clé→valeur (méthodologie éditable, §5) ---- */

    /** Représentation plate des paramètres numériques (points, cibles, global). */
    public Map<String, Double> toMap() {
        var m = new LinkedHashMap<String, Double>();
        putFundGrid(m, "gridA", gridA);
        putFundGrid(m, "gridB", gridB);
        putFundGrid(m, "gridD", gridD);
        putFundGrid(m, "gridE", gridE);
        putFundGrid(m, "gridF", gridF);
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
        m.put("global.possibleFloor", possibleFloor);
        m.put("global.scoreCap", scoreCap);
        m.put("global.targetAttainment", targetAttainment);
        m.put("global.vintageHalfLife", vintageHalfLife);
        m.put("global.maturityYoung", maturityYoung);
        m.put("global.maturityMature", maturityMature);
        return m;
    }

    private static void putFundGrid(Map<String, Double> m, String p, FundGrid g) {
        m.put(p + ".dpi.points", g.dpi().points());
        m.put(p + ".dpi.target", g.dpi().target());
        m.put(p + ".tv.points", g.tv().points());
        m.put(p + ".tv.target", g.tv().target());
        m.put(p + ".irr.points", g.irr().points());
        m.put(p + ".irr.target", g.irr().target());
        m.put(p + ".geo.points", g.geo().matchPoints());
        m.put(p + ".geo.other", g.geo().otherPoints());
    }

    /**
     * Construit un profil depuis des surcharges clé→valeur ; toute clé absente prend sa
     * valeur par défaut. Les ensembles géographiques restent fixes (non éditables).
     */
    public static ScoringProfile fromMap(Map<String, Double> overrides) {
        var d = defaults().toMap();
        Function<String, Double> g = k -> overrides.getOrDefault(k, d.get(k));
        Set<String> pref = Set.of("US", "EUROPE", "UK");

        DealGrid c = new DealGrid(
                new Ratio(g.apply("gridC.cagr.points"), g.apply("gridC.cagr.target")),
                new Ratio(g.apply("gridC.margin.points"), g.apply("gridC.margin.target")),
                new Ratio(g.apply("gridC.fcf.points"), g.apply("gridC.fcf.target")),
                new Ratio(g.apply("gridC.irr.points"), g.apply("gridC.irr.target")),
                new Geo(g.apply("gridC.geo.points"), g.apply("gridC.geo.other"), pref));

        return new ScoringProfile(
                g.apply("global.possibleFloor"), g.apply("global.scoreCap"),
                g.apply("global.targetAttainment"), g.apply("global.vintageHalfLife"),
                g.apply("global.maturityYoung"), g.apply("global.maturityMature"),
                fundGridFrom(g, "gridA", pref), fundGridFrom(g, "gridB", pref),
                fundGridFrom(g, "gridD", pref), fundGridFrom(g, "gridE", pref),
                fundGridFrom(g, "gridF", pref), c);
    }

    private static FundGrid fundGridFrom(Function<String, Double> g, String p, Set<String> pref) {
        return new FundGrid(
                new Ratio(g.apply(p + ".dpi.points"), g.apply(p + ".dpi.target")),
                new Ratio(g.apply(p + ".tv.points"), g.apply(p + ".tv.target")),
                new Ratio(g.apply(p + ".irr.points"), g.apply(p + ".irr.target")),
                new Geo(g.apply(p + ".geo.points"), g.apply(p + ".geo.other"), pref));
    }
}
