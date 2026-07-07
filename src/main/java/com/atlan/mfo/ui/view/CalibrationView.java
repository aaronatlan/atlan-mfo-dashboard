package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.Outcome;
import com.atlan.mfo.model.enums.Tier;
import com.atlan.mfo.ui.util.Formatters;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;

/**
 * Calibration prédit → réalisé : compare le score/les retours prédits au résultat
 * réel des opportunités décidées. La synthèse par bande de score répond à la
 * question clé — « les hauts scores performent-ils vraiment mieux ? » — de façon
 * interprétable, sans boîte noire.
 */
public final class CalibrationView extends ScrollPane {

    private static final String HINT =
            "As realized results are recorded on decided opportunities, this view shows whether "
                    + "higher scores actually performed better — the evidence to calibrate the "
                    + "methodology targets over time. Private-markets results take years, so early on "
                    + "this will be sparse.";

    public CalibrationView(List<Outcome> outcomes) {
        getStyleClass().add("detail-scroll");
        setFitToWidth(true);

        VBox body = new VBox(18);
        body.getStyleClass().add("detail-body");

        Label title = new Label("Calibration");
        title.getStyleClass().add("detail-title");
        Label hint = new Label(HINT);
        hint.getStyleClass().add("detail-paragraph");
        hint.setWrapText(true);
        body.getChildren().addAll(title, hint);

        if (outcomes.isEmpty()) {
            body.getChildren().add(card("Predicted vs realized",
                    paragraph("No outcomes recorded yet. Open a decided opportunity and use "
                            + "\"Outcome\" to record its realized result.")));
        } else {
            body.getChildren().add(card("By score band", bandSummary(outcomes)));
            body.getChildren().add(card("Recorded outcomes", table(outcomes)));
        }
        setContent(body);
    }

    /* ---- Synthèse par bande de score (interprétable) ---- */

    private javafx.scene.Node bandSummary(List<Outcome> outcomes) {
        GridPane g = new GridPane();
        g.getStyleClass().add("method-table");
        g.setHgap(28);
        g.setVgap(8);
        String[] heads = {"Score band", "Outcomes", "With result", "Realized IRR", "Expected IRR", "Δ IRR"};
        for (int c = 0; c < heads.length; c++) {
            Label h = new Label(heads[c]);
            h.getStyleClass().add("method-head");
            g.add(h, c, 0);
        }
        int r = 1;
        for (Tier t : new Tier[]{Tier.STRONG, Tier.MODERATE, Tier.CAUTION}) {
            List<Outcome> band = outcomes.stream()
                    .filter(o -> o.predictedScore() != null && Tier.fromScore(o.predictedScore()) == t)
                    .toList();
            List<Outcome> withIrr = band.stream().filter(o -> o.realizedIrr() != null).toList();
            Double realAvg = avg(withIrr, Outcome::realizedIrr);
            Double expAvg = avg(withIrr.stream().filter(o -> o.expectedIrr() != null).toList(), Outcome::expectedIrr);
            String delta = (realAvg != null && expAvg != null)
                    ? deltaPoints(realAvg - expAvg) : "—";

            cellAt(g, r, 0, t.label() + bandRange(t));
            cellAt(g, r, 1, Integer.toString(band.size()));
            cellAt(g, r, 2, Integer.toString(withIrr.size()));
            cellAt(g, r, 3, realAvg == null ? "—" : Formatters.percent(realAvg));
            cellAt(g, r, 4, expAvg == null ? "—" : Formatters.percent(expAvg));
            cellAt(g, r, 5, delta);
            r++;
        }
        return g;
    }

    private static String bandRange(Tier t) {
        return switch (t) {
            case STRONG -> " (70+)";
            case MODERATE -> " (40–69)";
            case CAUTION -> " (0–39)";
        };
    }

    private static Double avg(List<Outcome> list, Function<Outcome, Double> f) {
        var stats = list.stream().map(f).filter(v -> v != null).mapToDouble(Double::doubleValue);
        var summary = stats.summaryStatistics();
        return summary.getCount() == 0 ? null : summary.getAverage();
    }

    /** Écart en points de pourcentage, signé (ex. « -6.0 pts »). */
    private static String deltaPoints(double fractionDelta) {
        double pts = fractionDelta * 100.0;
        return String.format("%+.1f pts", pts);
    }

    /* ---- Table des outcomes ---- */

    private TableView<Outcome> table(List<Outcome> outcomes) {
        TableView<Outcome> table = new TableView<>();
        table.getStyleClass().add("opportunity-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(FXCollections.observableArrayList(outcomes));
        table.setPrefHeight(360);

        table.getColumns().add(col("Name", Outcome::name));
        table.getColumns().add(col("Strategy", Outcome::strategy));
        table.getColumns().add(col("Pred. score", o -> Formatters.score(o.predictedScore())));
        table.getColumns().add(col("Outcome", o -> o.outcome() == null ? "—" : o.outcome().label()));
        table.getColumns().add(col("Exp. IRR", o -> Formatters.percent(o.expectedIrr())));
        table.getColumns().add(col("Real. IRR", o -> Formatters.percent(o.realizedIrr())));
        table.getColumns().add(col("Exp. MOIC", o -> Formatters.multiple(o.expectedMoic())));
        table.getColumns().add(col("Real. MOIC", o -> Formatters.multiple(o.realizedMoic())));
        return table;
    }

    private static TableColumn<Outcome, String> col(String title, Function<Outcome, String> value) {
        TableColumn<Outcome, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(value.apply(cd.getValue())));
        c.getStyleClass().add("col-secondary");
        return c;
    }

    /* ---- Helpers ---- */

    private static void cellAt(GridPane g, int r, int c, String text) {
        Label l = new Label(text);
        l.getStyleClass().add("method-cell");
        g.add(l, c, r);
    }

    private static VBox card(String heading, javafx.scene.Node content) {
        Label t = new Label(heading.toUpperCase());
        t.getStyleClass().add("detail-card-title");
        VBox box = new VBox(14, t, content);
        box.getStyleClass().add("detail-card");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Label paragraph(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("detail-paragraph");
        l.setWrapText(true);
        return l;
    }
}
