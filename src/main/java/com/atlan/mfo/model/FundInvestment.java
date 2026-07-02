package com.atlan.mfo.model;

import com.atlan.mfo.model.enums.BenchmarkStatus;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.DealStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Fonds (Buyout/growth/VC, Secondaries, Private credit) — table {@code fund_investment}.
 *
 * <p>Les métriques financières sont nullable : {@code null} = « non communiqué »,
 * exclu du scoring (voir §4, §5.1).
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

        Integer recentVintage,
        Double recentDpi,
        Double recentTvpi,
        Double recentIrr,
        Double recentMoic,

        Integer earlierVintage,
        Double earlierDpi,
        Double earlierTvpi,
        Double earlierIrr,
        Double earlierMoic,

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
        Long updatedBy) {
}
