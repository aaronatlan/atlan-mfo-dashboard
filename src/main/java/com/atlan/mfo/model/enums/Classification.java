package com.atlan.mfo.model.enums;

import java.util.List;
import java.util.Locale;

/**
 * Vocabulaire de classification des marchés privés (dictionnaire de données analystes).
 *
 * <p>Concepts transversaux : {@code access_route} (route d'accès), les champs de
 * secondaires et les champs support sont modélisés séparément plutôt que dupliqués
 * dans chaque classe d'actifs. Le <b>scoring</b> reste piloté par les grilles
 * existantes (méthodologie IC) : ces valeurs servent à la <b>classification</b>, pas
 * (encore) au calcul de score.
 *
 * <p>Les enums imbriqués stockent leur {@link #name()} en base (robuste au renommage
 * d'affichage) ; les multi-sélections sont persistées en texte joint par virgules.
 */
public final class Classification {

    private Classification() {
    }

    /** Classe d'actifs (single-select). */
    public enum AssetClass {
        PRIVATE_EQUITY("Private Equity"),
        VENTURE_CAPITAL("Venture Capital"),
        PRIVATE_CREDIT("Private Credit"),
        REAL_ASSETS("Real Assets"),
        SECONDARIES("Secondaries"),
        HEDGE_FUNDS("Hedge Funds");

        private final String label;

        AssetClass(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Sous-stratégies proposées (les Secondaries n'en ont pas : cf. champs dédiés). */
        public List<String> subStrategies() {
            return switch (this) {
                case PRIVATE_EQUITY -> List.of(
                        "Buyout", "Growth Equity", "Turnaround / Special Situations", "Fund of Funds (PE)");
                case VENTURE_CAPITAL -> List.of(
                        "Pre-seed / Seed", "Early-stage (Series A/B)", "Late-stage",
                        "Sector-specific VC", "VC Fund of Funds");
                case PRIVATE_CREDIT -> List.of(
                        "Direct Lending", "Mezzanine / Subordinated Debt",
                        "Distressed Debt / Special Situations", "Venture Debt",
                        "Structured Credit / Asset-Based Lending", "Real Estate Debt",
                        "NAV Financing", "Royalties");
                case REAL_ASSETS -> List.of(
                        "Real Estate", "Infrastructure", "Natural Resources");
                case SECONDARIES, HEDGE_FUNDS -> List.of();
            };
        }
    }

    /** Route d'accès (multi-select) — transversale à toutes les classes. */
    public enum AccessRoute {
        PRIMARY_FUND("Primary Fund Commitment"),
        CO_INVESTMENT("Co-investment"),
        DIRECT_INVESTMENT("Direct Investment"),
        SECONDARY("Secondary");

        private final String label;

        AccessRoute(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Mandat de secondaire (multi-select) — renseigné si la route inclut {@code SECONDARY}. */
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

    /** Stratégie sous-jacente d'un secondaire (multi-select) — si route inclut {@code SECONDARY}. */
    public enum UnderlyingStrategy {
        BUYOUT("Buyout"),
        GROWTH_EQUITY("Growth Equity"),
        VENTURE_CAPITAL("Venture Capital"),
        PRIVATE_CREDIT("Private Credit"),
        REAL_ASSETS("Real Assets"),
        FOF_DIVERSIFIED("Fund-of-Funds / Diversified");

        private final String label;

        UnderlyingStrategy(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Type de véhicule (single-select, champ support). */
    public enum VehicleType {
        CLOSED_END("Closed-end fund"),
        EVERGREEN("Evergreen / Open-end"),
        SMA("SMA"),
        CO_INVEST_VEHICLE("Co-invest vehicle"),
        SPV("SPV"),
        CONTINUATION("Continuation fund");

        private final String label;

        VehicleType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Étape de cycle de vie (single-select, champ support). */
    public enum LifecycleStage {
        FUNDRAISING("Fundraising"),
        INVESTING("Investing"),
        HARVESTING("Harvesting"),
        WIND_DOWN("Wind-down");

        private final String label;

        LifecycleStage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /* ---- Helpers de (dé)sérialisation ---- */

    /** Résout un code vers une valeur d'enum, ou {@code null} si vide/inconnu. */
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
