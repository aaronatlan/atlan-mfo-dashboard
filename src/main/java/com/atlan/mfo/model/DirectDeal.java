package com.atlan.mfo.model;

import com.atlan.mfo.model.enums.BenchmarkStatus;
import com.atlan.mfo.model.enums.DealStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Deal direct (Co-investissement et direct) — table {@code direct_deal}.
 *
 * <p>Pourcentages et IRR stockés en fraction décimale ; multiples en nombre nu
 * (voir §4). Métriques nullable = « non communiqué ».
 */
public record DirectDeal(
        long id,
        String name,
        String nextSteps,
        DealStatus status,
        BenchmarkStatus vsBenchmark,
        String industry,
        String gp,
        String geography,
        String invType,
        Double commitment,

        Double revenue,
        Double cagrPct,
        Double ebitda,
        Double ebitdaGrPct,
        Double ebitdaMgnPct,
        Double fcf,
        Double fcfConvPct,
        Double ev,

        Double entryMult,
        String peersMult,
        Double exitVal,
        Double expIrrPct,
        Double expMoic,

        LocalDate dealDeadline,
        LocalDate targetExit,

        String comments,

        Integer scoreSnapshot,

        long version,
        OffsetDateTime updatedAt,
        Long updatedBy,

        // Contact de l'opportunité (GP, sponsor, intermédiaire…)
        String contactName,
        String contactEmail,
        String contactPhone,

        // Devise native du commitment (code ISO ; défaut USD). Agrégats convertis en USD.
        String currency,

        // Classification marchés privés (structure Patrimium). Le template direct découle
        // de accessRoute ∈ {CO_INVESTMENT, DIRECT_INVESTMENT}.
        String assetClass,          // Classification.AssetClass (code) — axe organisateur
        String subStrategy,         // libellé de sous-stratégie (enfant de assetClass)
        String accessRoute,         // Classification.AccessRoute (code, single)
        String secondaryMandate,    // Classification.SecondaryMandate — CSV (rare pour un deal)
        String underlyingStrategy) {// Classification.UnderlyingStrategy — CSV
}
