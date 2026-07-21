package com.atlan.mfo.model;

/**
 * Millésime d'un fonds (table {@code fund_vintage}). Un fonds en a 1..N.
 * Métriques nullable = « non communiqué », exclues du scoring (§5.1, §5.5).
 */
public record FundVintage(
        long id,
        long fundId,
        int vintageYear,
        Double dpi,
        Double tvpi,
        Double irr,
        Double moic,

        // Taille du fonds levé pour ce millésime et cible de levée (devise du fonds).
        Double fundSize,
        Double targetRaise,

        // Cash yield (rendement courant) — pertinent surtout pour le private credit. Fraction.
        Double cashYield) {
}
