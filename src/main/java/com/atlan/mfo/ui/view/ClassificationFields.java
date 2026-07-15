package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.enums.Classification;
import com.atlan.mfo.model.enums.Classification.AccessRoute;
import com.atlan.mfo.model.enums.Classification.AssetClass;
import com.atlan.mfo.model.enums.Classification.SecondaryMandate;
import com.atlan.mfo.model.enums.Classification.UnderlyingStrategy;
import com.atlan.mfo.ui.util.FormControls;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bloc de saisie de la <b>classification</b> (structure Patrimium), partagé par les
 * formulaires fonds et deals. Gère les dépendances : asset class → sous-stratégies, et
 * la section « secondaire » (mandat + sous-jacent) qui n'apparaît que pour un secondaire
 * (route {@code SECONDARY} ou classe {@code SECONDARIES}). Les routes d'accès proposées
 * dépendent du template (fonds : Primary/Secondary ; deal : Co-invest/Direct).
 */
public final class ClassificationFields extends VBox {

    private final ComboBox<AssetClass> assetClassCombo =
            FormControls.enumCombo(AssetClass.values(), AssetClass::label, true);
    private final ComboBox<String> subStrategyCombo = new ComboBox<>();
    private final ComboBox<AccessRoute> accessRouteCombo;
    private final Map<SecondaryMandate, CheckBox> mandates = new LinkedHashMap<>();
    private final Map<UnderlyingStrategy, CheckBox> underlyings = new LinkedHashMap<>();
    private final VBox secondaryBox = new VBox(8);

    public ClassificationFields(List<AccessRoute> allowedRoutes) {
        setSpacing(10);
        setAlignment(Pos.TOP_LEFT);

        accessRouteCombo = FormControls.enumCombo(
                allowedRoutes.toArray(new AccessRoute[0]), AccessRoute::label, true);

        subStrategyCombo.setEditable(true);
        subStrategyCombo.getEditor().setPromptText("sub-strategy");
        subStrategyCombo.setMaxWidth(Double.MAX_VALUE);
        assetClassCombo.valueProperty().addListener((o, a, b) -> {
            String kept = subStrategyCombo.getEditor().getText();
            subStrategyCombo.getItems().setAll(b == null ? List.of() : b.subStrategies());
            subStrategyCombo.getEditor().setText(kept);
            refreshSecondary();
        });
        accessRouteCombo.valueProperty().addListener((o, a, b) -> refreshSecondary());

        GridPane g = new GridPane();
        g.getStyleClass().add("form-grid");
        g.setHgap(18);
        g.setVgap(10);
        int r = 0;
        r = row(g, r, "Asset class", assetClassCombo);
        r = row(g, r, "Sub-strategy", subStrategyCombo);
        row(g, r, "Access route", accessRouteCombo);

        Label secTitle = new Label("SECONDARY");
        secTitle.getStyleClass().add("form-section");
        GridPane sg = new GridPane();
        sg.getStyleClass().add("form-grid");
        sg.setHgap(18);
        sg.setVgap(10);
        int sr = 0;
        sr = row(sg, sr, "Mandate", checkPane(SecondaryMandate.values(), SecondaryMandate::label, mandates));
        row(sg, sr, "Underlying", checkPane(UnderlyingStrategy.values(), UnderlyingStrategy::label, underlyings));
        secondaryBox.getChildren().addAll(secTitle, sg);
        secondaryBox.setVisible(false);
        secondaryBox.setManaged(false);

        getChildren().addAll(g, secondaryBox);
    }

    private <E extends Enum<E>> FlowPane checkPane(E[] values, java.util.function.Function<E, String> labeler,
                                                   Map<E, CheckBox> into) {
        FlowPane fp = new FlowPane(16, 6);
        for (E v : values) {
            CheckBox cb = new CheckBox(labeler.apply(v));
            into.put(v, cb);
            fp.getChildren().add(cb);
        }
        return fp;
    }

    private void refreshSecondary() {
        boolean secondary = accessRouteCombo.getValue() == AccessRoute.SECONDARY
                || assetClassCombo.getValue() == AssetClass.SECONDARIES;
        secondaryBox.setVisible(secondary);
        secondaryBox.setManaged(secondary);
    }

    private int row(GridPane g, int r, String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("detail-key");
        l.setMinWidth(150);
        g.add(l, 0, r);
        g.add(control, 1, r);
        GridPane.setHgrow(control, Priority.ALWAYS);
        return r + 1;
    }

    /* ---- Lecture des valeurs ---- */

    public String assetClass() {
        return assetClassCombo.getValue() == null ? null : assetClassCombo.getValue().name();
    }

    public String subStrategy() {
        String s = subStrategyCombo.getEditor().getText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public String accessRoute() {
        return accessRouteCombo.getValue() == null ? null : accessRouteCombo.getValue().name();
    }

    public String secondaryMandate() {
        return selectedCsv(mandates);
    }

    public String underlyingStrategy() {
        return selectedCsv(underlyings);
    }

    private static <E extends Enum<E>> String selectedCsv(Map<E, CheckBox> boxes) {
        StringBuilder sb = new StringBuilder();
        for (var e : boxes.entrySet()) {
            if (e.getValue().isSelected()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(e.getKey().name());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /* ---- Pré-remplissage (édition) ---- */

    public void populate(String assetClass, String subStrategy, String accessRoute,
                         String secondaryMandate, String underlyingStrategy) {
        assetClassCombo.setValue(Classification.fromCode(AssetClass.class, assetClass));
        if (subStrategy != null) {
            subStrategyCombo.getEditor().setText(subStrategy);
        }
        accessRouteCombo.setValue(Classification.fromCode(AccessRoute.class, accessRoute));
        check(mandates, Classification.listFromCsv(SecondaryMandate.class, secondaryMandate));
        check(underlyings, Classification.listFromCsv(UnderlyingStrategy.class, underlyingStrategy));
        refreshSecondary();
    }

    private static <E extends Enum<E>> void check(Map<E, CheckBox> boxes, List<E> on) {
        for (var e : boxes.entrySet()) {
            e.getValue().setSelected(on.contains(e.getKey()));
        }
    }

    /** Pré-sélectionne une classe d'actifs (création depuis une section), si vide. */
    public void preselect(AssetClass ac) {
        if (ac != null && assetClassCombo.getValue() == null) {
            assetClassCombo.setValue(ac);
        }
    }
}
