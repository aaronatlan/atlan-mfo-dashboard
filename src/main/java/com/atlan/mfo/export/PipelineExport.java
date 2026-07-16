package com.atlan.mfo.export;

import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.ui.util.Formatters;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Exporte le pipeline (opportunités) vers Excel (.xlsx) ou PDF.
 *
 * <p>Fonds et deals directs ne partagent pas les mêmes métriques (grilles A/B vs C,
 * §5.2-5.4) : les fonds ont un millésime/DPI, les deals un CAGR/marge EBITDA/multiple
 * d'entrée. Plutôt que des colonnes vides pour l'un ou l'autre, l'export sépare les deux
 * en sections/feuilles distinctes, chacune avec ses colonnes propres.
 */
public final class PipelineExport {

    private static final List<String> COMMON = List.of("Name", "Asset class", "Status", "Score");

    // Fonds : millésime le plus récent (§5.5). Excel garde Tier ; PDF non.
    // Commitment = devise native ; Commitment (USD) = converti (devise de référence).
    private static final List<String> FUND_XLSX_HEADERS = concat(COMMON,
            "Tier", "Vintage", "DPI", "IRR", "MOIC", "Geography", "Commitment", "Currency", "Commitment (USD)");
    private static final List<String> FUND_PDF_HEADERS = concat(COMMON,
            "Vintage", "DPI", "IRR", "MOIC", "Geography", "Commitment", "Currency", "Commitment (USD)");

    // Deals directs (grille C) : secteur, CAGR, marge EBITDA, multiple d'entrée, exit visée.
    private static final List<String> DEAL_XLSX_HEADERS = concat(COMMON,
            "Industry", "Tier", "Exp. IRR", "Exp. MOIC", "Revenue CAGR", "EBITDA Margin", "Entry Multiple",
            "Target Exit", "Geography", "Commitment", "Currency", "Commitment (USD)");
    private static final List<String> DEAL_PDF_HEADERS = concat(COMMON,
            "Industry", "Exp. IRR", "Exp. MOIC", "CAGR", "EBITDA %", "Entry x", "Target Exit", "Geography",
            "Commitment", "Currency", "Commitment (USD)");

    private PipelineExport() {
    }

    /** Excel : deux feuilles (Funds, Direct deals), nombres bruts pour manipulation. */
    public static void toXlsx(List<PipelineItem> items, Path file) throws IOException {
        List<PipelineItem> funds = items.stream().filter(i -> i.type() == PipelineItem.Type.FUND).toList();
        List<PipelineItem> deals = items.stream().filter(i -> i.type() == PipelineItem.Type.DEAL).toList();

        List<List<Object>> fundRows = new ArrayList<>();
        for (PipelineItem i : funds) {
            fundRows.add(Arrays.asList(
                    i.name(), i.assetClassLabel(), i.status().label(), i.score(),
                    i.tier() == null ? null : i.tier().label(),
                    i.vintageYear(), i.dpi(), i.irr(), i.moic(), i.geography(),
                    i.commitment(), i.currency(), i.commitmentUsd()));
        }
        List<List<Object>> dealRows = new ArrayList<>();
        for (PipelineItem i : deals) {
            dealRows.add(Arrays.asList(
                    i.name(), i.assetClassLabel(), i.status().label(), i.score(),
                    i.industry(),
                    i.tier() == null ? null : i.tier().label(),
                    i.irr(), i.moic(), i.dealCagr(), i.dealEbitdaMargin(), i.dealEntryMultiple(),
                    i.dealTargetExit() == null ? null : i.dealTargetExit().toString(),
                    i.geography(), i.commitment(), i.currency(), i.commitmentUsd()));
        }
        Xlsx.write(file, List.of(
                new Xlsx.Sheet("Funds", FUND_XLSX_HEADERS, fundRows),
                new Xlsx.Sheet("Direct deals", DEAL_XLSX_HEADERS, dealRows)));
    }

    /** PDF : deux sections (Funds, Direct deals), valeurs formatées pour l'affichage. */
    public static void toPdf(List<PipelineItem> items, Path file) throws IOException {
        List<PipelineItem> funds = items.stream().filter(i -> i.type() == PipelineItem.Type.FUND).toList();
        List<PipelineItem> deals = items.stream().filter(i -> i.type() == PipelineItem.Type.DEAL).toList();

        List<List<String>> fundRows = new ArrayList<>();
        for (PipelineItem i : funds) {
            fundRows.add(Arrays.asList(
                    i.name(), i.assetClassLabel(), i.status().label(), Formatters.score(i.score()),
                    i.vintageYear() == null ? "—" : Integer.toString(i.vintageYear()),
                    Formatters.multiple(i.dpi()), Formatters.percent(i.irr()), Formatters.multiple(i.moic()),
                    Formatters.text(i.geography()), Formatters.money(i.commitment()),
                    Formatters.text(i.currency()), Formatters.money(i.commitmentUsd())));
        }
        List<List<String>> dealRows = new ArrayList<>();
        for (PipelineItem i : deals) {
            dealRows.add(Arrays.asList(
                    i.name(), i.assetClassLabel(), i.status().label(), Formatters.score(i.score()),
                    Formatters.text(i.industry()),
                    Formatters.percent(i.irr()), Formatters.multiple(i.moic()),
                    Formatters.percent(i.dealCagr()), Formatters.percent(i.dealEbitdaMargin()),
                    Formatters.multiple(i.dealEntryMultiple()), Formatters.date(i.dealTargetExit()),
                    Formatters.text(i.geography()), Formatters.money(i.commitment()),
                    Formatters.text(i.currency()), Formatters.money(i.commitmentUsd())));
        }
        String subtitle = "Patrimium MFO — pipeline · " + LocalDate.now() + " · " + items.size() + " opportunities";
        Pdf.writeSections(file, "Investment pipeline", subtitle, List.of(
                new Pdf.Section("Fund commitments", FUND_PDF_HEADERS, fundRows),
                new Pdf.Section("Direct & co-investment deals", DEAL_PDF_HEADERS, dealRows)), true);
    }

    private static List<String> concat(List<String> base, String... extra) {
        List<String> l = new ArrayList<>(base);
        l.addAll(Arrays.asList(extra));
        return l;
    }
}
