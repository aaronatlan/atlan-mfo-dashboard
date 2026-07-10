package com.atlan.mfo.ui.view;

import com.atlan.mfo.scoring.ScoringProfile;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Méthodologie de scoring — <b>éditable</b> (voir §5). Permet d'ajuster les points
 * (poids) et cibles de chaque grille, plus les paramètres globaux. Enregistrer
 * recalcule tous les scores. Les régions géographiques préférées et les paliers de
 * timeline (en jours) restent fixes.
 */
public final class MethodologyView extends ScrollPane {

    private final Map<String, Double> current;
    private final Map<String, TextField> fields = new LinkedHashMap<>();
    private final Consumer<Map<String, Double>> onSave;
    private final Label errorLabel = new Label();

    public MethodologyView(ScoringProfile profile, Consumer<Map<String, Double>> onSave) {
        this.current = profile.toMap();
        this.onSave = onSave;
        getStyleClass().add("detail-scroll");
        setFitToWidth(true);

        VBox body = new VBox(18);
        body.getStyleClass().add("detail-body");

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setManaged(false);

        body.getChildren().addAll(
                title("Scoring methodology"),
                fundGridCard("Grid A — Buyout, growth, VC (and Secondaries)", "gridA"),
                fundGridCard("Grid B — Private credit", "gridB"),
                dealGridCard(),
                globalCard(),
                actions());

        setContent(body);
    }

    /* ---- Cartes de grille ---- */

    private VBox fundGridCard(String heading, String p) {
        GridPane g = table();
        int r = header(g);
        r = ratioRow(g, r, "DPI", p + ".dpi");
        r = ratioRow(g, r, "IRR", p + ".irr");
        r = ratioRow(g, r, "MOIC", p + ".moic");
        r = geoRow(g, r, p + ".geo");
        timelineRow(g, r, p + ".timeline");
        return card(heading, g);
    }

    private VBox dealGridCard() {
        GridPane g = table();
        int r = header(g);
        r = ratioRow(g, r, "Revenue CAGR", "gridC.cagr");
        r = ratioRow(g, r, "EBITDA Margin", "gridC.margin");
        r = ratioRow(g, r, "FCF Conversion", "gridC.fcf");
        r = ratioRow(g, r, "Expected IRR", "gridC.irr");
        r = geoRow(g, r, "gridC.geo");
        timelineRow(g, r, "gridC.timeline");
        return card("Grid C — Co-investment and direct", g);
    }

    private VBox globalCard() {
        GridPane g = new GridPane();
        g.getStyleClass().add("form-grid");
        g.setHgap(18);
        g.setVgap(10);
        int r = 0;
        r = globalRow(g, r, "Vintage half-life (years)", "global.vintageHalfLife");
        r = globalRow(g, r, "Denominator floor", "global.possibleFloor");
        globalRow(g, r, "Score cap", "global.scoreCap");
        return card("Global parameters", g);
    }

    /* ---- Lignes ---- */

    private int header(GridPane g) {
        String[] heads = {"Component", "Points", "Target"};
        for (int c = 0; c < heads.length; c++) {
            Label h = new Label(heads[c]);
            h.getStyleClass().add("method-head");
            g.add(h, c, 0);
        }
        return 1;
    }

    private int ratioRow(GridPane g, int r, String label, String base) {
        g.add(cell(label), 0, r);
        g.add(field(base + ".points"), 1, r);
        g.add(field(base + ".target"), 2, r);
        return r + 1;
    }

    private int geoRow(GridPane g, int r, String base) {
        g.add(cell("Geography (match · other)"), 0, r);
        g.add(field(base + ".points"), 1, r);
        g.add(field(base + ".other"), 2, r);
        return r + 1;
    }

    private int timelineRow(GridPane g, int r, String base) {
        g.add(cell("Timeline (fixed ≤30/60/90 days)"), 0, r);
        g.add(field(base + ".points"), 1, r);
        return r + 1;
    }

    private int globalRow(GridPane g, int r, String label, String key) {
        Label l = new Label(label);
        l.getStyleClass().add("detail-key");
        l.setMinWidth(220);
        g.add(l, 0, r);
        g.add(field(key), 1, r);
        return r + 1;
    }

    /* ---- Actions ---- */

    private HBox actions() {
        Button save = new Button("Save methodology");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> save());

        Button reset = new Button("Restore default values");
        reset.getStyleClass().add("ghost-button");
        reset.setOnAction(e -> {
            ScoringProfile.defaults().toMap().forEach((k, v) -> {
                if (fields.containsKey(k)) {
                    fields.get(k).setText(fmt(v));
                }
            });
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(12, errorLabel, spacer, reset, save);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.getStyleClass().add("method-actions");
        return box;
    }

    private void save() {
        hideError();
        Map<String, Double> out = new LinkedHashMap<>();
        for (var e : fields.entrySet()) {
            String txt = e.getValue().getText();
            try {
                out.put(e.getKey(), Double.parseDouble(txt.trim().replace(",", ".")));
            } catch (NumberFormatException ex) {
                showError("Invalid value for \"" + e.getKey() + "\": " + txt);
                return;
            }
        }
        onSave.accept(out);
    }

    /* ---- Helpers ---- */

    private TextField field(String key) {
        TextField t = new TextField(fmt(current.getOrDefault(key, 0d)));
        t.getStyleClass().add("form-control");
        t.setMaxWidth(110);
        t.setPrefWidth(110);
        fields.put(key, t);
        return t;
    }

    private static GridPane table() {
        GridPane g = new GridPane();
        g.getStyleClass().add("method-table");
        g.setHgap(24);
        g.setVgap(8);
        return g;
    }

    private static VBox card(String heading, javafx.scene.Node content) {
        Label t = new Label(heading.toUpperCase());
        t.getStyleClass().add("detail-card-title");
        VBox box = new VBox(14, t, content);
        box.getStyleClass().add("detail-card");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Label cell(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("method-cell");
        return l;
    }

    private static Label title(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("detail-title");
        return l;
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
