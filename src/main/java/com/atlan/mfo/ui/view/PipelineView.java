package com.atlan.mfo.ui.view;

import com.atlan.mfo.export.PipelineExport;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.ui.util.ErrorDialog;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

        getChildren().addAll(bar, kpi, table);
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
