package com.atlan.mfo.model;

import com.atlan.mfo.model.enums.BenchmarkStatus;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.DealStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fonds (Buyout/growth/VC, Secondaries, Private credit) — table {@code fund_investment}.
 *
 * <p>Les métriques financières sont nullable : {@code null} = « non communiqué »,
 * exclu du scoring (voir §4, §5.1). Le track record est porté par la liste des
 * millésimes {@code vintages} (table {@code fund_vintage}, voir §5.5).
 */
public record FundInvestment(
        long id,
        Category category,
        String name,
        String nextSteps,
        DealStatus status,
        BenchmarkStatus vsBenchmark,
        String geography,
        String assetClass,   // Classification.AssetClass (code) — axe organisateur ; la
                             // sous-stratégie détaillée est portée par subStrategy ci-dessous
        Double commitment,

        List<FundVintage> vintages,

        LocalDate firstClose,
        LocalDate finalClose,

        String comments,

        Integer scoreSnapshot,
        Double subDpi,
        Double subIrr,
        Double subMoic,
        Double subGeo,
        Double subTime,

        long version,
        OffsetDateTime updatedAt,
        Long updatedBy,

        // Contact de l'opportunité (GP, sponsor, intermédiaire…)
        String contactName,
        String contactEmail,
        String contactPhone,

        // Devise native du commitment (code ISO ; défaut USD). Agrégats convertis en USD.
        String currency,

        // Classification marchés privés (structure Patrimium). Le template fonds découle
        // de accessRoute ∈ {PRIMARY_FUND, SECONDARY}.
        String subStrategy,         // libellé de sous-stratégie (enfant de assetClass)
        String accessRoute,         // Classification.AccessRoute (code, single)
        String secondaryMandate,    // Classification.SecondaryMandate — CSV (secondaires)
        String underlyingStrategy) {// Classification.UnderlyingStrategy — CSV (secondaires)
}
