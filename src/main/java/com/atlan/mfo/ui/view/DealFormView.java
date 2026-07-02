package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.enums.BenchmarkStatus;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.scoring.ScoringEngine;
import com.atlan.mfo.ui.util.FormControls;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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

import java.time.LocalDate;
import java.util.function.BiConsumer;

/** Fiche deal direct éditable avec scoring en direct (grille C, voir §6.2). */
public final class DealFormView extends BorderPane {

    private final DirectDeal existing;
    private final ScoringEngine engine;
    private final BiConsumer<DirectDeal, ScoreBreakdown> onSave;

    private final ComboBox<DealStatus> statusCombo =
            FormControls.enumCombo(DealStatus.values(), DealStatus::label, false);
    private final ComboBox<BenchmarkStatus> benchCombo =
            FormControls.enumCombo(BenchmarkStatus.values(), BenchmarkStatus::label, true);
    private final ComboBox<String> geoCombo = FormControls.geographyCombo();

    private final TextField nameField = FormControls.field("nom du deal");
    private final TextField industryField = FormControls.field("secteur");
    private final TextField gpField = FormControls.field("GP / sponsor");
    private final TextField invTypeField = FormControls.field("type d'investissement");
    private final TextField commitmentField = FormControls.field("ex. 40m");
    private final TextField revenueField = FormControls.field("revenue");
    private final TextField cagrField = FormControls.field("ex. 47% ou 0.47");
    private final TextField ebitdaField = FormControls.field("EBITDA");
    private final TextField ebitdaGrField = FormControls.field("croissance EBITDA");
    private final TextField ebitdaMgnField = FormControls.field("ex. 25% ou 0.25");
    private final TextField fcfField = FormControls.field("FCF");
    private final TextField fcfConvField = FormControls.field("ex. 70% ou 0.70");
    private final TextField evField = FormControls.field("EV");
    private final TextField entryMultField = FormControls.field("multiple d'entrée");
    private final TextField peersMultField = FormControls.field("ex. 20-40x (informatif)");
    private final TextField exitValField = FormControls.field("valeur de sortie");
    private final TextField expIrrField = FormControls.field("ex. 32% ou 0.32");
    private final TextField expMoicField = FormControls.field("MOIC attendu");
    private final DatePicker deadlinePicker = new DatePicker();
    private final DatePicker targetExitPicker = new DatePicker();
    private final TextArea nextStepsArea = new TextArea();
    private final TextArea commentsArea = new TextArea();

    private final ScoreBreakdownView scorePanel = new ScoreBreakdownView();
    private final Label errorLabel = new Label();

    public DealFormView(DirectDeal existing, ScoringEngine engine,
                        BiConsumer<DirectDeal, ScoreBreakdown> onSave, Runnable onCancel) {
        this.existing = existing;
        this.engine = engine;
        this.onSave = onSave;
        getStyleClass().add("form-view");

        setTop(header(existing == null ? "Nouveau deal" : "Éditer — " + existing.name(), onCancel));
        setCenter(buildBody());
        populate();
        wireLiveScoring();
        recompute();
    }

    private HBox header(String title, Runnable onCancel) {
        Label t = new Label(title);
        t.getStyleClass().add("view-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button cancel = new Button("Annuler");
        cancel.getStyleClass().add("ghost-button");
        cancel.setOnAction(e -> onCancel.run());
        Button save = new Button("Enregistrer");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> save());
        HBox bar = new HBox(12, t, spacer, cancel, save);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("form-header");
        return bar;
    }

    private HBox buildBody() {
        GridPane g = new GridPane();
        g.getStyleClass().add("form-grid");
        g.setHgap(16);
        g.setVgap(10);
        int r = 0;
        r = row(g, r, "Nom", nameField);
        r = row(g, r, "Statut", statusCombo);
        r = row(g, r, "Vs. benchmark", benchCombo);
        r = row(g, r, "Secteur", industryField);
        r = row(g, r, "GP / sponsor", gpField);
        r = row(g, r, "Géographie", geoCombo);
        r = row(g, r, "Type d'investissement", invTypeField);
        r = row(g, r, "Capital envisagé", commitmentField);
        r = section(g, r, "PERFORMANCE FINANCIÈRE");
        r = row(g, r, "Revenue", revenueField);
        r = row(g, r, "Revenue CAGR", cagrField);
        r = row(g, r, "EBITDA", ebitdaField);
        r = row(g, r, "Croissance EBITDA", ebitdaGrField);
        r = row(g, r, "Marge EBITDA", ebitdaMgnField);
        r = row(g, r, "FCF", fcfField);
        r = row(g, r, "Conversion FCF", fcfConvField);
        r = row(g, r, "EV", evField);
        r = section(g, r, "RETOURS ATTENDUS");
        r = row(g, r, "Multiple d'entrée", entryMultField);
        r = row(g, r, "Multiples comparables", peersMultField);
        r = row(g, r, "Valeur de sortie", exitValField);
        r = row(g, r, "IRR attendu", expIrrField);
        r = row(g, r, "MOIC attendu", expMoicField);
        r = section(g, r, "TIMELINE");
        r = row(g, r, "Deadline", deadlinePicker);
        r = row(g, r, "Sortie cible", targetExitPicker);

        nextStepsArea.setPromptText("prochaines étapes");
        nextStepsArea.setPrefRowCount(2);
        commentsArea.setPromptText("commentaires");
        commentsArea.setPrefRowCount(3);
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setManaged(false);

        VBox form = new VBox(14, g,
                labeled("Prochaines étapes", nextStepsArea),
                labeled("Commentaires", commentsArea),
                errorLabel);
        form.getStyleClass().add("form-body");
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("form-scroll");
        HBox.setHgrow(scroll, Priority.ALWAYS);

        HBox content = new HBox(20, scroll, scorePanel);
        content.getStyleClass().add("form-content");
        return content;
    }

    private VBox labeled(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        return new VBox(4, l, control);
    }

    private int row(GridPane g, int r, String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        control.getStyleClass().add("form-control");
        if (control instanceof Region reg) {
            reg.setMinWidth(220);
        }
        g.add(l, 0, r);
        g.add(control, 1, r);
        return r + 1;
    }

    private int section(GridPane g, int r, String title) {
        Label l = new Label(title);
        l.getStyleClass().add("form-section");
        g.add(l, 0, r, 2, 1);
        return r + 1;
    }

    private void populate() {
        statusCombo.setValue(DealStatus.INITIAL_REVIEW);
        if (existing != null) {
            nameField.setText(existing.name());
            statusCombo.setValue(existing.status());
            benchCombo.setValue(existing.vsBenchmark());
            industryField.setText(existing.industry());
            gpField.setText(existing.gp());
            geoCombo.setValue(existing.geography());
            invTypeField.setText(existing.invType());
            commitmentField.setText(str(existing.commitment()));
            revenueField.setText(str(existing.revenue()));
            cagrField.setText(str(existing.cagrPct()));
            ebitdaField.setText(str(existing.ebitda()));
            ebitdaGrField.setText(str(existing.ebitdaGrPct()));
            ebitdaMgnField.setText(str(existing.ebitdaMgnPct()));
            fcfField.setText(str(existing.fcf()));
            fcfConvField.setText(str(existing.fcfConvPct()));
            evField.setText(str(existing.ev()));
            entryMultField.setText(str(existing.entryMult()));
            peersMultField.setText(existing.peersMult());
            exitValField.setText(str(existing.exitVal()));
            expIrrField.setText(str(existing.expIrrPct()));
            expMoicField.setText(str(existing.expMoic()));
            deadlinePicker.setValue(existing.dealDeadline());
            targetExitPicker.setValue(existing.targetExit());
            nextStepsArea.setText(existing.nextSteps());
            commentsArea.setText(existing.comments());
        }
    }

    private void wireLiveScoring() {
        cagrField.textProperty().addListener((o, a, b) -> recompute());
        ebitdaMgnField.textProperty().addListener((o, a, b) -> recompute());
        fcfConvField.textProperty().addListener((o, a, b) -> recompute());
        expIrrField.textProperty().addListener((o, a, b) -> recompute());
        geoCombo.valueProperty().addListener((o, a, b) -> recompute());
        deadlinePicker.valueProperty().addListener((o, a, b) -> recompute());
    }

    private void recompute() {
        scorePanel.update(engine.score(currentDeal(), LocalDate.now()));
    }

    private DirectDeal currentDeal() {
        long id = existing != null ? existing.id() : 0;
        long version = existing != null ? existing.version() : 0;
        return new DirectDeal(
                id,
                tn(nameField.getText()),
                tn(nextStepsArea.getText()),
                statusCombo.getValue(),
                benchCombo.getValue(),
                tn(industryField.getText()),
                tn(gpField.getText()),
                geoCombo.getValue(),
                tn(invTypeField.getText()),
                FormControls.parse(commitmentField.getText()),
                FormControls.parse(revenueField.getText()),
                FormControls.parse(cagrField.getText()),
                FormControls.parse(ebitdaField.getText()),
                FormControls.parse(ebitdaGrField.getText()),
                FormControls.parse(ebitdaMgnField.getText()),
                FormControls.parse(fcfField.getText()),
                FormControls.parse(fcfConvField.getText()),
                FormControls.parse(evField.getText()),
                FormControls.parse(entryMultField.getText()),
                tn(peersMultField.getText()),
                FormControls.parse(exitValField.getText()),
                FormControls.parse(expIrrField.getText()),
                FormControls.parse(expMoicField.getText()),
                deadlinePicker.getValue(),
                targetExitPicker.getValue(),
                tn(commentsArea.getText()),
                null, null, null, null, null, null, null,
                version, null, null);
    }

    private void save() {
        if (tn(nameField.getText()) == null) {
            showError("Le nom est obligatoire.");
            return;
        }
        DirectDeal d = currentDeal();
        onSave.accept(d, engine.score(d, LocalDate.now()));
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private static String tn(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String str(Double d) {
        return d == null ? "" : (d == Math.rint(d) ? Long.toString((long) (double) d) : Double.toString(d));
    }
}
