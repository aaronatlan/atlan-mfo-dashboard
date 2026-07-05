package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.PipelineItem;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/** Écran d'accueil : Pipeline summary (KPI + tableau global filtrable, voir §6.1). */
public final class PipelineView extends VBox {

    public PipelineView(List<PipelineItem> items, Consumer<PipelineItem> onOpen) {
        getStyleClass().add("view-root");
        setSpacing(22);

        Label header = new Label("Pipeline summary");
        header.getStyleClass().add("view-title");

        KpiBar kpi = new KpiBar(items);
        OpportunityTable table = new OpportunityTable(items, onOpen, true);
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(header, kpi, table);
    }
}
