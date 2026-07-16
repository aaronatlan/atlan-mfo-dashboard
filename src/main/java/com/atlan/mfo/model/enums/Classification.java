package com.atlan.mfo.model.enums;

import java.util.List;
import java.util.Locale;

/**
 * Vocabulaire de classification des marchés privés (structure Patrimium).
 *
 * <p>{@link AssetClass} est l'axe organisateur (nav, sections). {@link AccessRoute}
 * détermine le <b>template</b> de fiche : {@code PRIMARY_FUND}/{@code SECONDARY} →
 * template fonds (millésimes, grille DPI/IRR/MOIC) ; {@code CO_INVESTMENT}/
 * {@code DIRECT_INVESTMENT} → template direct (métriques opérationnelles, grille C).
 * Les champs de secondaires (mandat + sous-jacent) ne s'appliquent qu'aux secondaires.
 *
 * <p>Les enums imbriqués stockent leur {@link #name()} en base ; les multi-sélections
 * sont persistées en CSV de codes.
 */
public final class Classification {

    private Classification() {
    }

    /** Classe d'actifs (single-select) — axe organisateur de la navigation. */
    public enum AssetClass {
        PRIVATE_EQUITY("Private Equity"),
        VENTURE_CAPITAL("Venture Capital"),
        PRIVATE_CREDIT("Private Credit"),
        REAL_ASSETS("Real Assets"),
        SECONDARIES("Secondaries");

        private final String label;

        AssetClass(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Sous-stratégies proposées (les Secondaries utilisent mandat + sous-jacent). */
        public List<String> subStrategies() {
            return switch (this) {
                case PRIVATE_EQUITY -> List.of(
                        "Buyout", "Growth Equity", "Turnaround / Special Situations", "Fund of Funds");
                case VENTURE_CAPITAL -> List.of(
                        "Pre-Seed / Seed", "Early-Stage", "Late-Stage", "Sector-Specific", "Fund of Funds");
                case PRIVATE_CREDIT -> List.of(
                        "Direct Lending", "Mezzanine", "Distressed / Special Situations", "Venture Debt",
                        "Structured Credit / Asset-Based Lending", "Real Estate Debt", "NAV Financing", "Royalties");
                case REAL_ASSETS -> List.of(
                        "Real Estate", "Infrastructure", "Natural Resources");
                case SECONDARIES -> List.of();
            };
        }
    }

    /** Route d'accès (single-select) — détermine le template de fiche. */
    public enum AccessRoute {
        PRIMARY_FUND("Primary fund commitment"),
        CO_INVESTMENT("Co-investment"),
        DIRECT_INVESTMENT("Direct investment"),
        SECONDARY("Secondary");

        private final String label;

        AccessRoute(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Mandat de secondaire (multi-select) — renseigné pour les secondaires. */
    public enum SecondaryMandate {
        LP_LED("LP-led"),
        GP_LED("GP-led"),
        DIRECT("Direct");

        private final String label;

        SecondaryMandate(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Stratégie sous-jacente d'un secondaire (multi-select). */
    public enum UnderlyingStrategy {
        BUYOUT("Buyout"),
        GROWTH("Growth"),
        VENTURE_CAPITAL("Venture capital"),
        CREDIT("Credit"),
        REAL_ASSETS("Real assets"),
        DIVERSIFIED("Diversified");

        private final String label;

        UnderlyingStrategy(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /* ---- Helpers de (dé)sérialisation ---- */

    /** Résout un code (insensible à la casse) vers une valeur d'enum, ou {@code null}. */
    public static <E extends Enum<E>> E fromCode(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Libellé d'affichage d'un code, ou {@code null}. */
    public static <E extends Enum<E>> String label(Class<E> type, String code,
                                                   java.util.function.Function<E, String> labeler) {
        E v = fromCode(type, code);
        return v == null ? null : labeler.apply(v);
    }

    /** Codes joints par virgules → liste (multi-select persisté). */
    public static <E extends Enum<E>> List<E> listFromCsv(Class<E> type, String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        java.util.List<E> out = new java.util.ArrayList<>();
        for (String part : csv.split(",")) {
            E v = fromCode(type, part);
            if (v != null && !out.contains(v)) {
                out.add(v);
            }
        }
        return out;
    }

    /** Liste d'enums → codes joints par virgules, ou {@code null} si vide. */
    public static <E extends Enum<E>> String toCsv(List<E> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (E v : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(v.name());
        }
        return sb.toString();
    }

    /** Libellés (affichage) d'une liste de codes CSV, joints par « · ». */
    public static <E extends Enum<E>> String labelsFromCsv(Class<E> type, String csv,
                                                           java.util.function.Function<E, String> labeler) {
        List<E> vals = listFromCsv(type, csv);
        if (vals.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (E v : vals) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(labeler.apply(v));
        }
        return sb.toString();
    }
}
