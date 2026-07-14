package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.enums.Classification;
import com.atlan.mfo.model.enums.Classification.AccessRoute;
import com.atlan.mfo.model.enums.Classification.AssetClass;
import com.atlan.mfo.model.enums.Classification.LifecycleStage;
import com.atlan.mfo.model.enums.Classification.SecondaryMandate;
import com.atlan.mfo.model.enums.Classification.UnderlyingStrategy;
import com.atlan.mfo.model.enums.Classification.VehicleType;
import com.atlan.mfo.ui.util.FormControls;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bloc de saisie de la <b>classification marchés privés</b> (dictionnaire analystes),
 * partagé par les formulaires fonds et deals. Gère les dépendances : asset_class →
 * sous-stratégies, et la section « secondaire » (mandat + stratégie sous-jacente) qui
 * n'apparaît que si la route d'accès inclut « Secondary ». Multi-sélections persistées
 * en CSV de codes d'enum.
 */
public final class ClassificationFields extends VBox {

    private final ComboBox<AssetClass> assetClassCombo =
            FormControls.enumCombo(AssetClass.values(), AssetClass::label, true);
    private final ComboBox<String> subStrategyCombo = new ComboBox<>();
    private final Map<AccessRoute, CheckBox> accessRoutes = new LinkedHashMap<>();
    private final Map<SecondaryMandate, CheckBox> secondaryMandates = new LinkedHashMap<>();
    private final Map<UnderlyingStrategy, CheckBox> underlyingStrategies = new LinkedHashMap<>();
    private final ComboBox<VehicleType> vehicleCombo =
            FormControls.enumCombo(VehicleType.values(), VehicleType::label, true);
    private final ComboBox<LifecycleStage> lifecycleCombo =
            FormControls.enumCombo(LifecycleStage.values(), LifecycleStage::label, true);
    private final TextField sectorField = FormControls.field("e.g. Healthcare, Fintech");

    private final VBox secondaryBox = new VBox(8);

    public ClassificationFields() {
        setSpacing(10);
        setAlignment(Pos.TOP_LEFT);

        subStrategyCombo.setEditable(true);
        subStrategyCombo.getEditor().setPromptText("sub-strategy");
        subStrategyCombo.setMaxWidth(Double.MAX_VALUE);
        // Les sous-stratégies proposées suivent la classe d'actifs choisie.
        assetClassCombo.valueProperty().addListener((o, a, b) -> {
            String kept = subStrategyCombo.getEditor().getText();
            subStrategyCombo.getItems().setAll(b == null ? java.util.List.of() : b.subStrategies());
            subStrategyCombo.getEditor().setText(kept);
        });

        GridPane g = new GridPane();
        g.getStyleClass().add("form-grid");
        g.setHgap(18);
        g.setVgap(10);
        int r = 0;
        r = row(g, r, "Asset class", assetClassCombo);
        r = row(g, r, "Sub-strategy", subStrategyCombo);
        r = row(g, r, "Access route", routesPane());
        r = row(g, r, "Vehicle type", vehicleCombo);
        r = row(g, r, "Lifecycle stage", lifecycleCombo);
        row(g, r, "Sector focus", sectorField);

        // Section secondaire (conditionnelle).
        Label secTitle = new Label("SECONDARY (if access route includes Secondary)");
        secTitle.getStyleClass().add("form-section");
        GridPane sg = new GridPane();
        sg.getStyleClass().add("form-grid");
        sg.setHgap(18);
        sg.setVgap(10);
        int sr = 0;
        sr = row(sg, sr, "Secondary mandate", checkPane(SecondaryMandate.values(),
                SecondaryMandate::label, secondaryMandates));
        row(sg, sr, "Underlying strategy", checkPane(UnderlyingStrategy.values(),
                UnderlyingStrategy::label, underlyingStrategies));
        secondaryBox.getChildren().addAll(secTitle, sg);
        secondaryBox.setVisible(false);
        secondaryBox.setManaged(false);

        getChildren().addAll(g, secondaryBox);
        refreshSecondaryVisibility();
    }

    /* ---- Construction des contrôles ---- */

    private FlowPane routesPane() {
        FlowPane fp = new FlowPane(16, 6);
        for (AccessRoute route : AccessRoute.values()) {
            CheckBox cb = new CheckBox(route.label());
            cb.selectedProperty().addListener((o, a, b) -> refreshSecondaryVisibility());
            accessRoutes.put(route, cb);
            fp.getChildren().add(cb);
        }
        return fp;
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

    private void refreshSecondaryVisibility() {
        boolean secondary = accessRoutes.getOrDefault(AccessRoute.SECONDARY, new CheckBox()).isSelected();
        secondaryBox.setVisible(secondary);
        secondaryBox.setManaged(secondary);
    }

    private int row(GridPane g, int r, String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("detail-key");
        l.setMinWidth(150);
        g.add(l, 0, r);
        g.add(control, 1, r);
        GridPane.setHgrow(control, javafx.scene.layout.Priority.ALWAYS);
        return r + 1;
    }

    /* ---- Lecture des valeurs (pour la construction du record) ---- */

    public String assetClassPm() {
        return assetClassCombo.getValue() == null ? null : assetClassCombo.getValue().name();
    }

    public String subStrategy() {
        String s = subStrategyCombo.getEditor().getText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public String accessRoute() {
        return selectedCsv(accessRoutes);
    }

    public String secondaryMandate() {
        return selectedCsv(secondaryMandates);
    }

    public String underlyingStrategy() {
        return selectedCsv(underlyingStrategies);
    }

    public String vehicleType() {
        return vehicleCombo.getValue() == null ? null : vehicleCombo.getValue().name();
    }

    public String lifecycleStage() {
        return lifecycleCombo.getValue() == null ? null : lifecycleCombo.getValue().name();
    }

    public String sectorFocus() {
        String s = sectorField.getText();
        return (s == null || s.isBlank()) ? null : s.trim();
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

    public void populate(String assetClassPm, String subStrategy, String accessRoute,
                         String secondaryMandate, String underlyingStrategy,
                         String vehicleType, String lifecycleStage, String sectorFocus) {
        assetClassCombo.setValue(Classification.fromCode(AssetClass.class, assetClassPm));
        if (subStrategy != null) {
            subStrategyCombo.getEditor().setText(subStrategy);
        }
        check(accessRoutes, Classification.listFromCsv(AccessRoute.class, accessRoute));
        check(secondaryMandates, Classification.listFromCsv(SecondaryMandate.class, secondaryMandate));
        check(underlyingStrategies, Classification.listFromCsv(UnderlyingStrategy.class, underlyingStrategy));
        vehicleCombo.setValue(Classification.fromCode(VehicleType.class, vehicleType));
        lifecycleCombo.setValue(Classification.fromCode(LifecycleStage.class, lifecycleStage));
        if (sectorFocus != null) {
            sectorField.setText(sectorFocus);
        }
        refreshSecondaryVisibility();
    }

    private static <E extends Enum<E>> void check(Map<E, CheckBox> boxes, java.util.List<E> on) {
        for (var e : boxes.entrySet()) {
            e.getValue().setSelected(on.contains(e.getKey()));
        }
    }
}
