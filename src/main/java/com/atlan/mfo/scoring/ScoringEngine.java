package com.atlan.mfo.scoring;

import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.ScoreComponent;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.Tier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Moteur de scoring — module pur (aucune dépendance UI ni base). Calcule un
 * {@link ScoreBreakdown} pour un fonds ou un deal selon la méthodologie §5.
 *
 * <p>Le score mesure <b>la qualité seule</b> : il ne bouge que si les données bougent.
 * La proximité d'échéance est une donnée d'ordonnancement, pas de qualité — elle est
 * affichée à part (voir {@link Urgency}) et n'entre pas dans le calcul.
 */
public final class ScoringEngine {

    private final ScoringProfile profile;

    public ScoringEngine() {
        this(ScoringProfile.defaults());
    }

    public ScoringEngine(ScoringProfile profile) {
        this.profile = profile;
    }

    /* ---- Fonds (grilles A / B / D / E / F) ---- */

    public ScoreBreakdown score(FundInvestment fund) {
        return score(fund, LocalDate.now());
    }

    public ScoreBreakdown score(FundInvestment fund, LocalDate reference) {
        ScoringProfile.FundGrid g = profile.fundGrid(assetClassOf(fund));
        double k = profile.curveK();
        List<ScoreComponent> comps = new ArrayList<>();

        // Partage des points entre capital distribué (DPI) et valeur totale (TVPI/MOIC)
        // selon la maturité : la somme reste constante, seul le curseur bouge.
        double mat = maturity(fund.vintages(), reference);
        double dpiPoints = g.dpi().points() * mat;
        double tvPoints = g.multiplePoints() - dpiPoints;

        Double dpi = blended(fund.vintages(), FundVintage::dpi);
        Double tv = blended(fund.vintages(), v -> v.tvpi() != null ? v.tvpi() : v.moic());

        // Sous le seuil de maturité, le DPI ne pèse rien : on ne l'affiche pas plutôt que
        // de montrer un composant à 0/0 qui laisserait croire à une pénalité.
        if (dpiPoints >= 0.05) {
            comps.add(ratio("DPI", new ScoringProfile.Ratio(dpiPoints, g.dpi().target()), dpi, k));
        }
        comps.add(ratio("TVPI", new ScoringProfile.Ratio(tvPoints, g.tv().target()), tv, k));
        comps.add(ratio("IRR", g.irr(), blended(fund.vintages(), FundVintage::irr), k));

        return assemble(comps);
    }

    /** Classe d'actifs pilotant la grille ; repli legacy tant qu'un fonds n'est pas classé. */
    private static String assetClassOf(FundInvestment fund) {
        if (fund.assetClass() != null) {
            return fund.assetClass();
        }
        return fund.category() == Category.PRIVATE_CREDIT ? "PRIVATE_CREDIT" : null;
    }

    /* ---- Deals directs (grille C) ---- */

    public ScoreBreakdown score(DirectDeal deal) {
        return score(deal, LocalDate.now());
    }

    public ScoreBreakdown score(DirectDeal deal, LocalDate reference) {
        ScoringProfile.DealGrid g = profile.gridC;
        double k = profile.curveK();
        List<ScoreComponent> comps = new ArrayList<>();

        comps.add(ratio("Revenue CAGR", g.cagr(), deal.cagrPct(), k));
        comps.add(ratio("EBITDA Margin", g.margin(), deal.ebitdaMgnPct(), k));
        comps.add(ratio("FCF Conversion", g.fcf(), deal.fcfConvPct(), k));
        comps.add(ratio("Expected IRR", g.irr(), deal.expIrrPct(), k));

        return assemble(comps);
    }

    /* ---- Composants ---- */

    private ScoreComponent ratio(String label, ScoringProfile.Ratio spec, Double value, double k) {
        if (value == null) {
            return ScoreComponent.excluded(label, spec.points());
        }
        return ScoreComponent.scored(label, spec.points(), spec.subScore(value, k));
    }

    /* ---- Maturité du track record (§5.5) ---- */

    /**
     * Maturité dans [0..1] : 0 = track record trop jeune pour que le DPI ait un sens
     * (courbe en J — un fonds de millésime récent n'a mécaniquement pas distribué),
     * 1 = assez mûr pour être jugé dessus.
     *
     * <p>L'âge est la moyenne des millésimes <b>pondérée par récence</b>, avec la même
     * pondération que {@link #blended} : les deux mesures parlent ainsi du même track
     * record, et un fonds au track record ancien n'est pas rajeuni par un millésime
     * récent isolé.
     */
    private double maturity(List<FundVintage> vintages, LocalDate reference) {
        if (vintages == null || vintages.isEmpty()) {
            return 1.0;   // sans millésime, DPI et TVPI sont exclus de toute façon
        }
        int newest = vintages.stream().mapToInt(FundVintage::vintageYear).max().orElseThrow();
        double num = 0, den = 0;
        for (FundVintage v : vintages) {
            double weight = Math.pow(0.5, (newest - v.vintageYear()) / profile.vintageHalfLife);
            num += weight * (reference.getYear() - v.vintageYear());
            den += weight;
        }
        if (den == 0) {
            return 1.0;
        }
        double age = num / den;
        double span = profile.maturityMature - profile.maturityYoung;
        if (span <= 0) {
            return age >= profile.maturityMature ? 1.0 : 0.0;
        }
        return Math.max(0.0, Math.min((age - profile.maturityYoung) / span, 1.0));
    }

    /* ---- Agrégation multi-millésimes, pondérée par récence (§5.5) ---- */
    /* ---- poids = 0,5 ^ (âge ÷ demi-vie) ---- */

    private Double blended(List<FundVintage> vintages, Function<FundVintage, Double> metric) {
        if (vintages == null || vintages.isEmpty()) {
            return null;
        }
        int newest = vintages.stream().mapToInt(FundVintage::vintageYear).max().orElseThrow();
        double num = 0, den = 0;
        for (FundVintage v : vintages) {
            Double value = metric.apply(v);
            if (value == null) {
                continue;
            }
            double weight = Math.pow(0.5, (newest - v.vintageYear()) / profile.vintageHalfLife);
            num += weight * value;
            den += weight;
        }
        return den == 0 ? null : num / den;
    }

    /* ---- Normalisation (§5.1) ---- */

    private ScoreBreakdown assemble(List<ScoreComponent> comps) {
        double earned = 0, possible = 0;
        for (ScoreComponent c : comps) {
            if (c.communicated()) {
                earned += c.subScore();
                possible += c.maxPoints();
            }
        }
        double raw = earned / Math.max(possible, profile.possibleFloor) * 100.0;
        int score = (int) Math.round(Math.min(raw, profile.scoreCap));
        return new ScoreBreakdown(List.copyOf(comps), earned, possible, score, Tier.fromScore(score));
    }
}
