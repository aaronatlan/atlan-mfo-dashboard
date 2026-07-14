package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.FxRates;
import com.atlan.mfo.model.enums.Currency;
import javafx.geometry.Pos;
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
 * Taux de change <b>éditables</b> vers l'USD (devise de référence, §4). Pour chaque
 * devise : la valeur d'1 unité en USD. Enregistrer recalcule tous les agrégats
 * (capital under review, allocation…). À rafraîchir manuellement au besoin — aucun
 * accès réseau (confidentiel, hors-ligne).
 */
public final class FxRatesView extends ScrollPane {

    private final Map<String, TextField> fields = new LinkedHashMap<>();
    private final Consumer<Map<String, Double>> onSave;
    private final Label errorLabel = new Label();

    public FxRatesView(FxRates rates, Consumer<Map<String, Double>> onSave) {
        this.onSave = onSave;
        getStyleClass().add("detail-scroll");
        setFitToWidth(true);

        VBox body = new VBox(18);
        body.getStyleClass().add("detail-body");

        Label title = new Label("Exchange rates");
        title.getStyleClass().add("detail-title");
        Label hint = new Label("Reference currency is USD. Each opportunity keeps its own currency; "
                + "global figures (total commitment, capital under review, allocation) are converted to USD "
                + "using the rates below. Update them manually as needed.");
        hint.getStyleClass().add("detail-paragraph");
        hint.setWrapText(true);

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setManaged(false);

        body.getChildren().addAll(title, hint, ratesCard(rates), actions());
        setContent(body);
    }

    private VBox ratesCard(FxRates rates) {
        GridPane g = new GridPane();
        g.getStyleClass().add("method-table");
        g.setHgap(24);
        g.setVgap(8);

        String[] heads = {"Currency", "USD per unit"};
        for (int c = 0; c < heads.length; c++) {
            Label h = new Label(heads[c]);
            h.getStyleClass().add("method-head");
            g.add(h, c, 0);
        }

        int r = 1;
        Map<String, Double> current = rates.asMap();
        for (Currency ccy : Currency.values()) {
            Label name = new Label(ccy.code() + " — " + ccy.label());
            name.getStyleClass().add("method-cell");
            g.add(name, 0, r);

            TextField t = new TextField(fmt(current.getOrDefault(ccy.code(), ccy.defaultUsdPerUnit())));
            t.getStyleClass().add("form-control");
            t.setMaxWidth(130);
            t.setPrefWidth(130);
            if (ccy == Currency.REFERENCE) {
                t.setDisable(true);   // USD = 1.0 par définition
            } else {
                fields.put(ccy.code(), t);
            }
            g.add(t, 1, r);
            r++;
        }
        return card("Rates to USD", g);
    }

    private HBox actions() {
        Button save = new Button("Save rates");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> save());

        Button reset = new Button("Restore default values");
        reset.getStyleClass().add("ghost-button");
        reset.setOnAction(e -> {
            for (Currency ccy : Currency.values()) {
                TextField t = fields.get(ccy.code());
                if (t != null) {
                    t.setText(fmt(ccy.defaultUsdPerUnit()));
                }
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(12, errorLabel, spacer, reset, save);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("method-actions");
        return box;
    }

    private void save() {
        hideError();
        Map<String, Double> out = new LinkedHashMap<>();
        for (var e : fields.entrySet()) {
            String txt = e.getValue().getText();
            try {
                double v = Double.parseDouble(txt.trim().replace(",", "."));
                if (v <= 0) {
                    showError("Rate for " + e.getKey() + " must be positive: " + txt);
                    return;
                }
                out.put(e.getKey(), v);
            } catch (NumberFormatException ex) {
                showError("Invalid rate for " + e.getKey() + ": " + txt);
                return;
            }
        }
        onSave.accept(out);
    }

    private static VBox card(String heading, javafx.scene.Node content) {
        Label t = new Label(heading.toUpperCase());
        t.getStyleClass().add("detail-card-title");
        VBox box = new VBox(14, t, content);
        box.getStyleClass().add("detail-card");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
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
