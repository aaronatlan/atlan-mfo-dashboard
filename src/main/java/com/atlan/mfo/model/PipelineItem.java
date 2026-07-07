package com.atlan.mfo.model;

import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.model.enums.Tier;

/**
 * Projection d'une opportunité (fonds ou deal) pour les tableaux de listes
 * (Pipeline summary et sections). Le tier est dérivé du score (§5.6).
 */
public record PipelineItem(
        long id,
        Type type,
        String name,
        Category category,   // null pour les deals
        String strategy,     // libellé de stratégie affiché
        DealStatus status,
        Integer score,       // peut être null (non scoré)
        Double commitment,
        int reported,        // critères de scoring renseignés
        int criteria) {      // total de critères de la grille

    public enum Type {
        FUND, DEAL
    }

    /** Libellé de stratégie unique pour la colonne et les filtres. */
    public static final String DEALS_STRATEGY = "Co-investment & direct";

    public Tier tier() {
        return score == null ? null : Tier.fromScore(score);
    }

    public boolean isActive() {
        return status.isActive();
    }

    /** Une décision a été prise (approuvé ou décliné) : reste au tableau, marqué « Decided ». */
    public boolean isDecided() {
        return !status.isActive();
    }

    /** Complétude des données de scoring, ex. « 4/6 ». */
    public String completeness() {
        return reported + "/" + criteria;
    }

    /** Le score est recalculé en direct par le moteur (§13.4), pas lu depuis score_snapshot. */
    public static PipelineItem ofFund(FundInvestment f, ScoreBreakdown b) {
        return new PipelineItem(
                f.id(), Type.FUND, f.name(), f.category(),
                f.category().label(), f.status(), b.score(), f.commitment(),
                b.reportedCount(), b.criteriaCount());
    }

    public static PipelineItem ofDeal(DirectDeal d, ScoreBreakdown b) {
        return new PipelineItem(
                d.id(), Type.DEAL, d.name(), null,
                DEALS_STRATEGY, d.status(), b.score(), d.commitment(),
                b.reportedCount(), b.criteriaCount());
    }
}
