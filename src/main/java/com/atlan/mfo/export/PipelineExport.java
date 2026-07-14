package com.atlan.mfo.export;

import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.ui.util.Formatters;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exporte le pipeline (opportunités) vers Excel (.xlsx) ou PDF. */
public final class PipelineExport {

    // Excel : garde Tier, valeurs brutes (score, DPI/IRR/MOIC, commitment) pour manipulation.
    private static final List<String> XLSX_HEADERS = List.of(
            "Name", "Strategy", "Status", "Score", "Tier",
            "Vintage", "DPI", "IRR", "MOIC", "Geography", "Commitment");

    // PDF : sans Tier, valeurs formatées ; paysage pour loger les colonnes.
    private static final List<String> PDF_HEADERS = List.of(
            "Name", "Strategy", "Status", "Score",
            "Vintage", "DPI", "IRR", "MOIC", "Geography", "Commitment");
    private static final double[] PDF_WEIGHTS = {20, 13, 11, 6, 7, 7, 7, 7, 8, 9};

    private PipelineExport() {
    }

    /** Excel : nombres bruts (l'utilisateur formate dans le tableur ; IRR/DPI = fractions/multiples). */
    public static void toXlsx(List<PipelineItem> items, Path file) throws IOException {
        List<List<Object>> rows = new ArrayList<>();
        for (PipelineItem i : items) {
            rows.add(Arrays.asList(
                    i.name(),
                    i.strategy(),
                    i.status().label(),
                    i.score(),
                    i.tier() == null ? null : i.tier().label(),
                    i.vintageYear(),
                    i.dpi(),
                    i.irr(),
                    i.moic(),
                    i.geography(),
                    i.commitment()));
        }
        Xlsx.write(file, "Pipeline", XLSX_HEADERS, rows);
    }

    /** PDF : valeurs formatées pour l'affichage (IC / partage). */
    public static void toPdf(List<PipelineItem> items, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (PipelineItem i : items) {
            rows.add(Arrays.asList(
                    i.name(),
                    i.strategy(),
                    i.status().label(),
                    Formatters.score(i.score()),
                    i.vintageYear() == null ? "—" : Integer.toString(i.vintageYear()),
                    Formatters.multiple(i.dpi()),
                    Formatters.percent(i.irr()),
                    Formatters.multiple(i.moic()),
                    Formatters.text(i.geography()),
                    Formatters.money(i.commitment())));
        }
        String subtitle = "Patrimium MFO — pipeline · " + LocalDate.now() + " · "
                + items.size() + " opportunities";
        Pdf.writeTable(file, "Investment pipeline", subtitle, PDF_HEADERS, PDF_WEIGHTS, rows, true);
    }
}
