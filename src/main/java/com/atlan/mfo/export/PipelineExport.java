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

    private static final List<String> HEADERS =
            List.of("Name", "Strategy", "Status", "Score", "Tier", "Data", "Commitment");
    private static final double[] WEIGHTS = {26, 20, 16, 8, 12, 8, 14};

    private PipelineExport() {
    }

    /** Excel : nombres bruts (score, commitment) pour manipulation dans le tableur. */
    public static void toXlsx(List<PipelineItem> items, Path file) throws IOException {
        List<List<Object>> rows = new ArrayList<>();
        for (PipelineItem i : items) {
            rows.add(Arrays.asList(
                    i.name(),
                    i.strategy(),
                    i.status().label(),
                    i.score(),
                    i.tier() == null ? null : i.tier().label(),
                    i.completeness(),
                    i.commitment()));
        }
        Xlsx.write(file, "Pipeline", HEADERS, rows);
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
                    i.tier() == null ? "—" : i.tier().label(),
                    i.completeness(),
                    Formatters.money(i.commitment())));
        }
        String subtitle = "Patrimium MFO — pipeline · " + LocalDate.now() + " · "
                + items.size() + " opportunities";
        Pdf.writeTable(file, "Investment pipeline", subtitle, HEADERS, WEIGHTS, rows);
    }
}
