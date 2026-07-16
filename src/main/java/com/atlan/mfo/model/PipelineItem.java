package com.atlan.mfo.model;

import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.Classification;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.model.enums.Tier;

import java.util.Comparator;

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
        int criteria,        // total de critères de la grille
        // Métriques d'export : fonds = millésime le plus récent ; deals = retours attendus
        Integer vintageYear, // année du millésime le plus récent (fonds), null pour deals
        Double dpi,
        Double irr,          // fonds : IRR du millésime récent ; deals : IRR attendu
        Double moic,
        String geography,    // token géographique canonique (US, EUROPE, UK…)
        // Métriques propres aux deals directs (null pour les fonds) : la grille C (§5.4)
        // ne partage pas DPI/millésime avec les grilles fonds A/B.
        Double dealCagr,
        Double dealEbitdaMargin,
        Double dealEntryMultiple,
        java.time.LocalDate dealTargetExit,
        String industry,      // secteur (deals directs uniquement), null pour les fonds
        String currency,      // devise native du commitment (code ISO ; défaut USD)
        Double commitmentUsd, // commitment converti en USD (agrégats), null si non renseigné
        String assetClass,    // Classification.AssetClass (code) — axe organisateur des sections
        String subStrategy,   // libellé de sous-stratégie (filtre)
        String accessRoute) { // Classification.AccessRoute (code) — filtre

    public enum Type {
        FUND, DEAL
    }

    /** Libellé de stratégie unique pour la colonne et les filtres. */
    public static final String DEALS_STRATEGY = "Co-investment & direct";

    public Tier tier() {
        return score == null ? null : Tier.fromScore(score);
    }

    /**
     * Libellé de la classe d'actifs — l'axe organisateur réel depuis la refonte de la
     * classification. Repli sur {@link #strategy} (libellé legacy) tant qu'une
     * opportunité n'est pas classée.
     */
    public String assetClassLabel() {
        String l = Classification.label(
                Classification.AssetClass.class, assetClass, Classification.AssetClass::label);
        return l != null ? l : strategy;
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
    public static PipelineItem ofFund(FundInvestment f, ScoreBreakdown b, FxRates fx) {
        FundVintage newest = f.vintages() == null ? null : f.vintages().stream()
                .max(Comparator.comparingInt(FundVintage::vintageYear)).orElse(null);
        return new PipelineItem(
                f.id(), Type.FUND, f.name(), f.category(),
                f.category().label(), f.status(), b.score(), f.commitment(),
                b.reportedCount(), b.criteriaCount(),
                newest == null ? null : newest.vintageYear(),
                newest == null ? null : newest.dpi(),
                newest == null ? null : newest.irr(),
                newest == null ? null : newest.moic(),
                f.geography(),
                null, null, null, null, null,
                f.currency(), fx.toUsd(f.commitment(), f.currency()),
                f.assetClass(), f.subStrategy(), f.accessRoute());
    }

    public static PipelineItem ofDeal(DirectDeal d, ScoreBreakdown b, FxRates fx) {
        return new PipelineItem(
                d.id(), Type.DEAL, d.name(), null,
                DEALS_STRATEGY, d.status(), b.score(), d.commitment(),
                b.reportedCount(), b.criteriaCount(),
                null, null, d.expIrrPct(), d.expMoic(), d.geography(),
                d.cagrPct(), d.ebitdaMgnPct(), d.entryMult(), d.targetExit(), d.industry(),
                d.currency(), fx.toUsd(d.commitment(), d.currency()),
                d.assetClass(), d.subStrategy(), d.accessRoute());
    }
}
