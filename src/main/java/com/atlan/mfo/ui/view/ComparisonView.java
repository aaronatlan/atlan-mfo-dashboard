package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.ui.util.Formatters;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Vue comparaison : deux ou trois opportunités côte à côte (scorecards identiques
 * à la fiche détail) pour trancher en comité. Chaque colonne est indépendante, ce
 * qui permet de comparer un fonds et un deal malgré des grilles différentes.
 */
public final class ComparisonView extends VBox {

    private final List<PipelineItem> items;
    private final Function<PipelineItem, ScoreBreakdown> scorer;
    private final List<ComboBox<PipelineItem>> pickers = new java.util.ArrayList<>();
    private final HBox columns = new HBox(16);

    public ComparisonView(List<PipelineItem> items, Function<PipelineItem, ScoreBreakdown> scorer) {
        this.items = items;
        this.scorer = scorer;
        getStyleClass().add("view-root");
        setSpacing(18);

        Label header = new Label("Compare");
        header.getStyleClass().add("view-title");
        Label hint = new Label("Select two or three opportunities to compare their scorecards side by side.");
        hint.getStyleClass().add("help-text");

        HBox pickerRow = new HBox(12);
        String[] prompts = {"First opportunity", "Second opportunity", "Third (optional)"};
        for (int i = 0; i < 3; i++) {
            PipelineItem initial = i < 2 && i < items.size() ? items.get(i) : null;
            ComboBox<PipelineItem> cb = picker(prompts[i], initial);
            pickers.add(cb);
            pickerRow.getChildren().add(cb);
        }

        columns.getStyleClass().add("compare-columns");
        ScrollPane scroll = new ScrollPane(columns);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("detail-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, hint, pickerRow, scroll);
        render();
    }

    private ComboBox<PipelineItem> picker(String prompt, PipelineItem initial) {
        ComboBox<PipelineItem> cb = new ComboBox<>();
        cb.getItems().setAll(items);
        cb.setConverter(new StringConverter<>() {
            @Override
            public String toString(PipelineItem p) {
                return p == null ? "" : p.name();
            }

            @Override
            public PipelineItem fromString(String s) {
                return null;
            }
        });
        cb.setPromptText(prompt);
        cb.setPrefWidth(260);
        if (initial != null) {
            cb.setValue(initial);
        }
        cb.valueProperty().addListener((o, a, b) -> render());
        return cb;
    }

    private void render() {
        columns.getChildren().clear();
        List<PipelineItem> chosen = pickers.stream()
                .map(ComboBox::getValue)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (chosen.size() < 2) {
            Label empty = new Label("Select at least two opportunities above.");
            empty.getStyleClass().add("help-text");
            columns.getChildren().add(empty);
            return;
        }
        for (PipelineItem item : chosen) {
            columns.getChildren().add(card(item));
        }
    }

    private Node card(PipelineItem item) {
        ScoreBreakdown b = scorer.apply(item);

        Label name = new Label(item.name());
        name.getStyleClass().add("detail-card-title");
        name.setWrapText(true);
        String cls = com.atlan.mfo.model.enums.Classification.label(
                com.atlan.mfo.model.enums.Classification.AssetClass.class, item.assetClass(),
                com.atlan.mfo.model.enums.Classification.AssetClass::label);
        Label subtitle = new Label((cls != null ? cls : item.strategy()) + "  ·  " + item.status().label());
        subtitle.getStyleClass().add("detail-subtitle");

        Label score = new Label(Formatters.score(b.score()));
        score.getStyleClass().add("detail-score");
        HBox scoreRow = new HBox(12, score, OpportunityTable.tierNode(b.tier()));
        scoreRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(14, name, subtitle, scoreRow, DetailView.scoreBars(b));
        box.getStyleClass().add("detail-card");
        box.getStyleClass().add("compare-card");
        box.setPrefWidth(320);
        box.setMaxHeight(Double.MAX_VALUE);
        return box;
    }
}
