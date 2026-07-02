package com.atlan.mfo.scoring;

import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.ScoreComponent;
import com.atlan.mfo.model.enums.Tier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Moteur de scoring — module pur (aucune dépendance UI ni base). Calcule un
 * {@link ScoreBreakdown} pour un fonds ou un deal selon la méthodologie §5.
 */
public final class ScoringEngine {

    private final ScoringProfile profile;

    public ScoringEngine() {
        this(ScoringProfile.defaults());
    }

    public ScoringEngine(ScoringProfile profile) {
        this.profile = profile;
    }

    /* ---- Fonds (grilles A / B) ---- */

    public ScoreBreakdown score(FundInvestment fund) {
        return score(fund, LocalDate.now());
    }

    public ScoreBreakdown score(FundInvestment fund, LocalDate reference) {
        ScoringProfile.FundGrid g = profile.fundGrid(fund.category());
        List<ScoreComponent> comps = new ArrayList<>();

        comps.add(ratio("DPI", g.dpi(), blended(fund.vintages(), FundVintage::dpi)));
        comps.add(ratio("IRR", g.irr(), blended(fund.vintages(), FundVintage::irr)));
        comps.add(ratio("MOIC", g.moic(), blended(fund.vintages(), FundVintage::moic)));
        comps.add(geo("Géographie", g.geo(), fund.geography()));
        comps.add(timeline("Timeline", g.timelinePoints(), fund.finalClose(), reference));

        return assemble(comps);
    }

    /* ---- Deals directs (grille C) ---- */

    public ScoreBreakdown score(DirectDeal deal) {
        return score(deal, LocalDate.now());
    }

    public ScoreBreakdown score(DirectDeal deal, LocalDate reference) {
        ScoringProfile.DealGrid g = profile.gridC;
        List<ScoreComponent> comps = new ArrayList<>();

        comps.add(ratio("Revenue CAGR", g.cagr(), deal.cagrPct()));
        comps.add(ratio("EBITDA Margin", g.margin(), deal.ebitdaMgnPct()));
        comps.add(ratio("FCF Conversion", g.fcf(), deal.fcfConvPct()));
        comps.add(ratio("Expected IRR", g.irr(), deal.expIrrPct()));
        comps.add(geo("Géographie", g.geo(), deal.geography()));
        comps.add(timeline("Timeline", g.timelinePoints(), deal.dealDeadline(), reference));

        return assemble(comps);
    }

    /* ---- Composants ---- */

    private ScoreComponent ratio(String label, ScoringProfile.Ratio spec, Double value) {
        if (value == null) {
            return ScoreComponent.excluded(label, spec.points());
        }
        return ScoreComponent.scored(label, spec.points(), spec.subScore(value));
    }

    private ScoreComponent geo(String label, ScoringProfile.Geo spec, String rawGeography) {
        String canonical = GeographyMatcher.normalize(rawGeography);
        if (canonical == null) {
            return ScoreComponent.excluded(label, spec.matchPoints());
        }
        double pts = GeographyMatcher.isMatch(canonical, spec.preferred())
                ? spec.matchPoints() : spec.otherPoints();
        return ScoreComponent.scored(label, spec.matchPoints(), pts);
    }

    private ScoreComponent timeline(String label, double points, LocalDate target, LocalDate reference) {
        OptionalDouble frac = TimelineScorer.fraction(
                target, reference, profile.timelineDays, profile.timelineFraction);
        if (frac.isEmpty()) {
            return ScoreComponent.excluded(label, points);
        }
        return ScoreComponent.scored(label, points, frac.getAsDouble() * points);
    }

    /* ---- Agrégation multi-millésimes, pondérée par récence (§5.5) ---- */

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
