package com.atlan.mfo.model;

import java.util.List;

/**
 * Référentiel des secteurs proposables (deals directs) — source unique, partagée par la
 * saisie ({@code DealFormView}) et le filtre ({@code OpportunityTable}), pour que ce
 * dernier propose toujours la liste complète même si un secteur n'est pas encore
 * représenté dans le pipeline actuel.
 */
public final class Industries {

    private Industries() {
    }

    public static final List<String> ALL = List.of(
            "Technology", "Software", "Fintech", "Financial services", "Healthcare", "Biotech",
            "Energy", "Consumer", "Retail", "Industrials", "Real estate", "Media & telecom",
            "Business services", "Materials", "Transport & logistics", "Education",
            "Agriculture & food", "Other");
}
