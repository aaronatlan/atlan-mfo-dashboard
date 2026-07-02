package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.PipelineItem;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/** Liste d'une section (fonds d'une catégorie ou deals directs) en lecture (§6.1). */
public final class SectionView extends VBox {

    public SectionView(String title, List<PipelineItem> items, Consumer<PipelineItem> onOpen) {
        getStyleClass().add("view-root");
        setSpacing(16);

        Label header = new Label(title);
        header.getStyleClass().add("view-title");

        OpportunityTable table = new OpportunityTable(items, onOpen, false);
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(header, table);
    }
}
