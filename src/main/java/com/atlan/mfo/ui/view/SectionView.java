package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.PipelineItem;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/** Liste d'une section (fonds d'une catégorie ou deals directs) en lecture (§6.1). */
public final class SectionView extends VBox {

    /** Variante lecture seule (sans bouton « + New ») : journal des décisions (§6.1). */
    public SectionView(String title, List<PipelineItem> items, Consumer<PipelineItem> onOpen) {
        getStyleClass().add("view-root");
        setSpacing(22);

        Label header = new Label(title);
        header.getStyleClass().add("view-title");

        OpportunityTable table = new OpportunityTable(items, onOpen, true);
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(header, table);
    }

    public SectionView(String title, List<PipelineItem> items, Consumer<PipelineItem> onOpen, Runnable onNew) {
        getStyleClass().add("view-root");
        setSpacing(22);

        Label header = new Label(title);
        header.getStyleClass().add("view-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = new Button("+ New");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> onNew.run());
        HBox bar = new HBox(12, header, spacer, add);
        bar.setAlignment(Pos.CENTER_LEFT);

        OpportunityTable table = new OpportunityTable(items, onOpen, false);
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(bar, table);
    }
}
