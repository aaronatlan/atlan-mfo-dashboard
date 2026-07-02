package com.atlan.mfo.scoring;

import com.atlan.mfo.model.enums.Category;

import java.util.Set;

/**
 * Poids, cibles et paramètres des trois grilles de scoring (voir §5). Centralisé
 * ici pour pouvoir ajuster la méthodologie sans toucher au moteur.
 */
public final class ScoringProfile {

    /** Métrique en ratio : {@code sous-score = MIN(valeur / cible, 1) × points}. */
    public record Ratio(double points, double target) {
        public double subScore(double value) {
            return Math.min(value / target, 1.0) * points;
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

    /** Grille appliquée à un fonds selon sa catégorie. */
    public FundGrid fundGrid(Category category) {
        return category == Category.PRIVATE_CREDIT ? gridB : gridA;
    }

    /** Configuration par défaut (méthodologie §5). */
    public static ScoringProfile defaults() {
        Set<String> fundPreferred = Set.of("US", "EUROPE", "UK", "DACH");
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
}
