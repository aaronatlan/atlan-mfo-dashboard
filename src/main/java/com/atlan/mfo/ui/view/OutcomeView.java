package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.Outcome;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.OutcomeState;
import com.atlan.mfo.ui.util.FormControls;
import com.atlan.mfo.ui.util.Formatters;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Éditeur de résultat réalisé d'une opportunité décidée (boucle prédit → réalisé).
 * Affiche le <b>prédit</b> (score, retours attendus) en lecture et laisse saisir le
 * <b>réalisé</b> (IRR/MOIC/DPI), l'état et une note.
 */
public final class OutcomeView extends BorderPane {

    private static final String HINT =
            "Record the actual result once known (exit, write-off…). Comparing realized to "
                    + "predicted feeds the calibration of the scoring methodology over time.";

    private final PipelineItem item;
    private final boolean isFund;
    private final Integer predictedScore;
    private final Double expectedIrr;
    private final Double expectedMoic;
    private final Consumer<Outcome> onSave;

    private final ComboBox<OutcomeState> stateCombo =
            FormControls.enumCombo(OutcomeState.values(), OutcomeState::label, true);
    private final TextField realizedIrr = new TextField();
    private final TextField realizedMoic = new TextField();
    private final TextField realizedDpi = new TextField();
    private final TextArea note = new TextArea();

    public OutcomeView(PipelineItem item, Integer predictedScore, Double expectedIrr, Double expectedMoic,
                       Outcome existing, Consumer<Outcome> onSave, Runnable onBack) {
        this.item = item;
        this.isFund = item.type() == PipelineItem.Type.FUND;
        this.predictedScore = predictedScore;
        this.expectedIrr = expectedIrr;
        this.expectedMoic = expectedMoic;
        this.onSave = onSave;
        getStyleClass().add("detail-view");

        if (existing != null) {
            stateCombo.setValue(existing.outcome());
            realizedIrr.setText(existing.realizedIrr() == null ? "" : Formatters.percent(existing.realizedIrr()));
            realizedMoic.setText(existing.realizedMoic() == null ? "" : Formatters.multiple(existing.realizedMoic()));
            realizedDpi.setText(existing.realizedDpi() == null ? "" : Formatters.multiple(existing.realizedDpi()));
            note.setText(existing.note() == null ? "" : existing.note());
        }

        setTop(header(onBack));
        setCenter(scroll());
    }

    private HBox header(Runnable onBack) {
        Button back = new Button("← Back");
        back.getStyleClass().add("ghost-button");
        back.setOnAction(e -> onBack.run());

        Label title = new Label(item.name());
        title.getStyleClass().add("detail-title");
        Label sub = new Label("Outcome  ·  " + item.strategy());
        sub.getStyleClass().add("detail-subtitle");
        VBox titles = new VBox(2, title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button save = new Button("Save outcome");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> save());

        HBox bar = new HBox(16, back, titles, spacer, save);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("detail-header");
        return bar;
    }

    private ScrollPane scroll() {
        GridPane layout = new GridPane();
        layout.getStyleClass().add("detail-layout");
        layout.setHgap(16);
        layout.setVgap(16);
        var half = new javafx.scene.layout.ColumnConstraints();
        half.setPercentWidth(50);
        layout.getColumnConstraints().addAll(half, half);

        layout.add(card("Predicted (at decision)", predictedGrid()), 0, 0);
        layout.add(card("Realized", realizedGrid()), 1, 0);
        VBox noteCard = card("Note", note);
        note.getStyleClass().add("form-control");
        note.setPrefRowCount(3);
        note.setWrapText(true);
        layout.add(noteCard, 0, 1);
        GridPane.setColumnSpan(noteCard, 2);

        Label hint = new Label(HINT);
        hint.getStyleClass().add("detail-paragraph");
        hint.setWrapText(true);
        VBox body = new VBox(16, layout, hint);

        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("detail-scroll");
        return sp;
    }

    private GridPane predictedGrid() {
        GridPane g = grid();
        addRow(g, 0, "Predicted score", Formatters.score(predictedScore));
        addRow(g, 1, "Expected IRR", Formatters.percent(expectedIrr));
        addRow(g, 2, "Expected MOIC", Formatters.multiple(expectedMoic));
        return g;
    }

    private GridPane realizedGrid() {
        GridPane g = grid();
        realizedIrr.setPromptText("e.g. 24% or 0.24");
        realizedMoic.setPromptText("e.g. 2.4x");
        realizedDpi.setPromptText("e.g. 1.5x");
        realizedIrr.getStyleClass().add("form-control");
        realizedMoic.getStyleClass().add("form-control");
        realizedDpi.getStyleClass().add("form-control");
        addRow(g, 0, "Outcome", stateCombo);
        addRow(g, 1, "Realized IRR", realizedIrr);
        addRow(g, 2, "Realized MOIC", realizedMoic);
        if (isFund) {
            addRow(g, 3, "Realized DPI", realizedDpi);
        }
        return g;
    }

    private void save() {
        Outcome out = new Outcome(
                null,
                item.type().name(),
                item.id(),
                item.name(),
                item.strategy(),
                predictedScore,
                expectedIrr,
                expectedMoic,
                stateCombo.getValue(),
                FormControls.parse(realizedIrr.getText()),
                FormControls.parse(realizedMoic.getText()),
                isFund ? FormControls.parse(realizedDpi.getText()) : null,
                blankToNull(note.getText()));
        onSave.accept(out);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /* ---- Helpers de mise en page (alignés sur DetailView) ---- */

    private static VBox card(String title, javafx.scene.Node content) {
        Label t = new Label(title.toUpperCase());
        t.getStyleClass().add("detail-card-title");
        VBox box = new VBox(14, t, content);
        box.getStyleClass().add("detail-card");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.getStyleClass().add("detail-grid");
        g.setHgap(24);
        g.setVgap(10);
        return g;
    }

    private static void addRow(GridPane g, int r, String key, String value) {
        addRow(g, r, key, new Label(value == null ? "—" : value) {{ getStyleClass().add("detail-value"); }});
    }

    private static void addRow(GridPane g, int r, String key, javafx.scene.Node value) {
        Label k = new Label(key);
        k.getStyleClass().add("detail-key");
        k.setMinWidth(140);
        g.add(k, 0, r);
        g.add(value, 1, r);
    }
}
