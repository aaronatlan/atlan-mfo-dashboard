package com.atlan.mfo.ui.util;

import com.atlan.mfo.dao.DirectDealDao;
import com.atlan.mfo.dao.FundInvestmentDao;
import com.atlan.mfo.dao.ScoringConfig;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.scoring.ScoringEngine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Charge toutes les opportunités en {@link PipelineItem} avec score recalculé en direct (§13.4). */
public final class PipelineLoader {

    private PipelineLoader() {
    }

    public static List<PipelineItem> loadItems() {
        ScoringEngine engine = new ScoringConfig().currentEngine();
        LocalDate today = LocalDate.now();
        List<PipelineItem> items = new ArrayList<>();
        new FundInvestmentDao().findAll()
                .forEach(f -> items.add(PipelineItem.ofFund(f, engine.score(f, today).score())));
        new DirectDealDao().findAll()
                .forEach(d -> items.add(PipelineItem.ofDeal(d, engine.score(d, today).score())));
        return items;
    }
}
