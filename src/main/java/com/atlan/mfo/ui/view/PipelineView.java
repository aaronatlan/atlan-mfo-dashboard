package com.atlan.mfo.ui.view;

import com.atlan.mfo.export.PipelineExport;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.Tier;
import com.atlan.mfo.ui.util.ErrorDialog;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/** Écran d'accueil : Pipeline summary (KPI + tableau global filtrable, voir §6.1). */
public final class PipelineView extends VBox {

    private final List<PipelineItem> items;

    public PipelineView(List<PipelineItem> items, Consumer<PipelineItem> onOpen) {
        this.items = items;
        getStyleClass().add("view-root");
        setSpacing(22);

        Label header = new Label("Pipeline summary");
        header.getStyleClass().add("view-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button excel = new Button("Export Excel");
        excel.getStyleClass().add("ghost-button");
        excel.setOnAction(e -> export(true));
        Button pdf = new Button("Export PDF");
        pdf.getStyleClass().add("ghost-button");
        pdf.setOnAction(e -> export(false));
        HBox bar = new HBox(12, header, spacer, excel, pdf);
        bar.setAlignment(Pos.CENTER_LEFT);

        KpiBar kpi = new KpiBar(items);
        OpportunityTable table = new OpportunityTable(items, onOpen, true);
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(bar, kpi, tierDistribution(items), table);
    }

    /* ---- Distribution des scores par tier (barres verticales) ---- */

    private VBox tierDistribution(List<PipelineItem> items) {
        List<PipelineItem> active = items.stream().filter(PipelineItem::isActive).toList();
        Tier[] tiers = {Tier.STRONG, Tier.MODERATE, Tier.CAUTION};
        long[] counts = new long[tiers.length];
        for (PipelineItem i : active) {
            for (int k = 0; k < tiers.length; k++) {
                if (i.tier() == tiers[k]) {
                    counts[k]++;
                }
            }
        }
        long max = 0;
        for (long c : counts) {
            max = Math.max(max, c);
        }

        HBox bars = new HBox(48);
        bars.setAlignment(Pos.BOTTOM_LEFT);
        for (int k = 0; k < tiers.length; k++) {
            bars.getChildren().add(tierColumn(tiers[k], counts[k], max));
        }

        Label title = new Label("SCORE DISTRIBUTION BY TIER");
        title.getStyleClass().add("pres-section-title");
        VBox panel = new VBox(16, title, bars);
        panel.getStyleClass().add("tier-panel");
        panel.setMaxWidth(Double.MAX_VALUE);
        return panel;
    }

    private VBox tierColumn(Tier tier, long count, long max) {
        double maxHeight = 120;
        double barHeight = max > 0 ? (double) count / max * maxHeight : 0;

        Region fill = new Region();
        fill.getStyleClass().addAll("tier-fill", switch (tier) {
            case STRONG -> "tier-fill-strong";
            case MODERATE -> "tier-fill-moderate";
            case CAUTION -> "tier-fill-caution";
        });
        fill.setPrefWidth(72);
        fill.setMaxWidth(72);
        fill.setMinHeight(barHeight);
        fill.setPrefHeight(barHeight);
        fill.setMaxHeight(barHeight);

        Label value = new Label(Long.toString(count));
        value.getStyleClass().add("tier-dist-value");
        StackPane.setAlignment(value, Pos.BOTTOM_CENTER);
        value.setTranslateY(-(barHeight + 4));

        StackPane plot = new StackPane(fill, value);
        plot.setAlignment(Pos.BOTTOM_CENTER);
        plot.setMinSize(72, maxHeight);
        plot.setPrefSize(72, maxHeight);
        plot.setMaxSize(72, maxHeight);

        Label name = new Label(tier.label());
        name.getStyleClass().add("tier-dist-name");
        Label range = new Label(switch (tier) {
            case STRONG -> "70+";
            case MODERATE -> "40–69";
            case CAUTION -> "0–39";
        });
        range.getStyleClass().add("tier-dist-range");
        VBox caption = new VBox(1, name, range);
        caption.setAlignment(Pos.CENTER);

        VBox col = new VBox(8, plot, caption);
        col.setAlignment(Pos.BOTTOM_CENTER);
        return col;
    }

    /** Exporte le pipeline courant vers un fichier choisi par l'utilisateur. */
    private void export(boolean excel) {
        FileChooser fc = new FileChooser();
        String ext = excel ? "xlsx" : "pdf";
        fc.setInitialFileName("patrimium-pipeline-" + LocalDate.now() + "." + ext);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                excel ? "Excel workbook" : "PDF document", "*." + ext));
        File file = fc.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            if (excel) {
                PipelineExport.toXlsx(items, file.toPath());
            } else {
                PipelineExport.toPdf(items, file.toPath());
            }
        } catch (Exception ex) {
            ErrorDialog.show(ex);
        }
    }
}
