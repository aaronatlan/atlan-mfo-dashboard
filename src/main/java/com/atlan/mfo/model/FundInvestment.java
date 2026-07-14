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
        String assetClass,
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

        // Classification marchés privés (§ dictionnaire analystes). Codes d'enum / CSV /
        // texte libre ; sert à la classification, pas encore au scoring.
        // (assetClassPm : distinct de l'ancien champ libre assetClass ci-dessus.)
        String assetClassPm,        // Classification.AssetClass (name)
        String subStrategy,         // libellé de sous-stratégie
        String accessRoute,         // Classification.AccessRoute — CSV (multi)
        String secondaryMandate,    // Classification.SecondaryMandate — CSV (si Secondary)
        String underlyingStrategy,  // Classification.UnderlyingStrategy — CSV (si Secondary)
        String vehicleType,         // Classification.VehicleType (name)
        String lifecycleStage,      // Classification.LifecycleStage (name)
        String sectorFocus) {       // texte libre
}
