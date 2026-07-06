package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.ScoreComponent;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Panneau de score recalculé en direct pendant la saisie (voir §6.2). */
public final class ScoreBreakdownView extends VBox {

    public ScoreBreakdownView() {
        getStyleClass().add("score-panel");
        setSpacing(12);
        setMinWidth(240);
        setPrefWidth(260);
    }

    public void update(ScoreBreakdown b) {
        getChildren().clear();

        Label caption = new Label("LIVE SCORE");
        caption.getStyleClass().add("score-panel-caption");

        Label value = new Label(Integer.toString(b.score()));
        value.getStyleClass().add("score-panel-value");

        HBox tier = OpportunityTable.tierNode(b.tier());

        GridPane grid = new GridPane();
        grid.getStyleClass().add("method-table");
        grid.setHgap(16);
        grid.setVgap(5);
        int r = 0;
        for (ScoreComponent c : b.components()) {
            Label label = new Label(c.label());
            label.getStyleClass().add("method-cell");
            Label sub = new Label(c.communicated() ? String.format("%.1f", c.subScore()) : "excluded");
            sub.getStyleClass().add("method-cell");
            GridPane.setHalignment(sub, javafx.geometry.HPos.RIGHT);
            grid.add(label, 0, r);
            grid.add(sub, 1, r);
            r++;
        }
        Region spacer = new Region();
        spacer.setMinHeight(4);

        Label footer = new Label(String.format("%.0f / %.0f points", b.earned(), b.possible()));
        footer.getStyleClass().add("score-panel-footer");

        HBox head = new HBox(12, value, tier);
        head.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(caption, head, grid, spacer, footer);
    }
}
