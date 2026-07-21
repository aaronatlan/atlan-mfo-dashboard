package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.DirectDeal;
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
import java.util.function.Consumer;

/** Fiche deal direct éditable avec scoring en direct (grille C, voir §6.2). */
public final class DealFormView extends BorderPane {

    private final DirectDeal existing;
    private final ScoringEngine engine;
    private final Consumer<DirectDeal> onSave;

    private final ComboBox<DealStatus> statusCombo =
            FormControls.enumCombo(DealStatus.values(), DealStatus::label, false);
    private final ComboBox<BenchmarkStatus> benchCombo =
            FormControls.enumCombo(BenchmarkStatus.values(), BenchmarkStatus::label, true);
    private final ComboBox<String> geoCombo = FormControls.geographyCombo();
    private final RegionSelect targetRegions = new RegionSelect();
    private final ClassificationFields classification = new ClassificationFields(java.util.List.of(
            com.atlan.mfo.model.enums.Classification.AccessRoute.CO_INVESTMENT,
            com.atlan.mfo.model.enums.Classification.AccessRoute.DIRECT_INVESTMENT));

    private final TextField nameField = FormControls.field("deal name");
    private final ComboBox<String> industryCombo = FormControls.editableCombo("industry",
            "Technology", "Software", "Fintech", "Financial services", "Healthcare", "Biotech",
            "Energy", "Consumer", "Retail", "Industrials", "Real estate", "Media & telecom",
            "Business services", "Materials", "Transport & logistics", "Education",
            "Agriculture & food", "Other");
    private final TextField gpField = FormControls.field("GP / sponsor");
    private final TextField invTypeField = FormControls.field("investment type");
    private final TextField commitmentField = FormControls.field("e.g. 40m");
    private final ComboBox<String> currencyCombo = FormControls.currencyCombo();
    private final TextField revenueField = FormControls.field("revenue");
    private final TextField cagrField = FormControls.field("e.g. 47% or 0.47");
    private final TextField ebitdaField = FormControls.field("EBITDA");
    private final TextField ebitdaGrField = FormControls.field("EBITDA growth");
    private final TextField ebitdaMgnField = FormControls.field("e.g. 25% or 0.25");
    private final TextField fcfField = FormControls.field("FCF");
    private final TextField fcfConvField = FormControls.field("e.g. 70% or 0.70");
    private final TextField evField = FormControls.field("EV");
    private final TextField entryMultField = FormControls.field("entry multiple");
    private final TextField peersMultField = FormControls.field("e.g. 20-40x (informational)");
    private final TextField exitValField = FormControls.field("exit value");
    private final TextField expIrrField = FormControls.field("e.g. 32% or 0.32");
    private final TextField expMoicField = FormControls.field("expected MOIC");
    private final DatePicker deadlinePicker = new DatePicker();
    private final DatePicker targetExitPicker = new DatePicker();
    private final TextField contactNameField = FormControls.field("first & last name");
    private final TextField contactEmailField = FormControls.field("email");
    private final TextField contactPhoneField = FormControls.field("phone");
    private final TextArea nextStepsArea = new TextArea();
    private final TextArea commentsArea = new TextArea();

    private final ScoreBreakdownView scorePanel = new ScoreBreakdownView();
    private final Label errorLabel = new Label();

    public DealFormView(DirectDeal existing, ScoringEngine engine,
                        Consumer<DirectDeal> onSave, Runnable onCancel) {
        this(existing, null, engine, onSave, onCancel);
    }

    public DealFormView(DirectDeal existing,
                        com.atlan.mfo.model.enums.Classification.AssetClass presetAssetClass,
                        ScoringEngine engine,
                        Consumer<DirectDeal> onSave, Runnable onCancel) {
        this.existing = existing;
        this.engine = engine;
        this.onSave = onSave;
        getStyleClass().add("form-view");

        setTop(header(existing == null ? "New deal" : "Edit — " + existing.name(), onCancel));
        setCenter(buildBody());
        populate();
        if (existing == null) {
            classification.preselect(presetAssetClass);
        }
        wireLiveScoring();
        recompute();
    }

    private HBox header(String title, Runnable onCancel) {
        Label t = new Label(title);
        t.getStyleClass().add("view-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost-button");
        cancel.setOnAction(e -> onCancel.run());
        Button save = new Button("Save");
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
        r = row(g, r, "Name", nameField);
        r = row(g, r, "Status", statusCombo);
        r = row(g, r, "Vs. benchmark", benchCombo);
        r = row(g, r, "Industry", industryCombo);
        r = row(g, r, "GP / sponsor", gpField);
        r = row(g, r, "Geography (GP HQ)", geoCombo);
        r = row(g, r, "Target regions", targetRegions);
        r = row(g, r, "Investment type", invTypeField);
        r = row(g, r, "Planned commitment", commitmentField);
        r = row(g, r, "Currency", currencyCombo);
        r = section(g, r, "FINANCIAL PERFORMANCE");
        r = row(g, r, "Revenue", revenueField);
        r = row(g, r, "Revenue CAGR", cagrField);
        r = row(g, r, "EBITDA", ebitdaField);
        r = row(g, r, "EBITDA growth", ebitdaGrField);
        r = row(g, r, "EBITDA margin", ebitdaMgnField);
        r = row(g, r, "FCF", fcfField);
        r = row(g, r, "FCF conversion", fcfConvField);
        r = row(g, r, "EV", evField);
        r = section(g, r, "EXPECTED RETURNS");
        r = row(g, r, "Entry multiple", entryMultField);
        r = row(g, r, "Peer multiples", peersMultField);
        r = row(g, r, "Exit value", exitValField);
        r = row(g, r, "Expected IRR", expIrrField);
        r = row(g, r, "Expected MOIC", expMoicField);
        r = section(g, r, "TIMELINE");
        r = row(g, r, "Deadline", deadlinePicker);
        r = row(g, r, "Target exit", targetExitPicker);
        r = section(g, r, "CONTACT");
        r = row(g, r, "Contact name", contactNameField);
        r = row(g, r, "Contact email", contactEmailField);
        r = row(g, r, "Contact phone", contactPhoneField);
        r = section(g, r, "CLASSIFICATION");
        g.add(classification, 0, r, 2, 1);
        r++;

        nextStepsArea.setPromptText("next steps");
        nextStepsArea.setPrefRowCount(2);
        commentsArea.setPromptText("comments");
        commentsArea.setPrefRowCount(3);
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setManaged(false);

        VBox form = new VBox(14, g,
                labeled("Next steps", nextStepsArea),
                labeled("Comments", commentsArea),
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
            industryCombo.getEditor().setText(existing.industry());
            gpField.setText(existing.gp());
            geoCombo.setValue(existing.geography());
            targetRegions.populate(existing.targetRegions());
            invTypeField.setText(existing.invType());
            commitmentField.setText(str(existing.commitment()));
            currencyCombo.setValue(com.atlan.mfo.model.enums.Currency.fromCode(existing.currency()).code());
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
            contactNameField.setText(existing.contactName());
            contactEmailField.setText(existing.contactEmail());
            contactPhoneField.setText(existing.contactPhone());
            classification.populate(existing.assetClass(), existing.subStrategy(), existing.accessRoute(),
                    existing.secondaryMandate(), existing.underlyingStrategy());
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
                FormControls.comboText(industryCombo),
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
                version, null, null,
                tn(contactNameField.getText()),
                tn(contactEmailField.getText()),
                tn(contactPhoneField.getText()),
                currencyCombo.getValue(),
                classification.assetClass(),
                classification.subStrategy(),
                classification.accessRoute(),
                classification.secondaryMandate(),
                classification.underlyingStrategy(),
                targetRegions.csv());
    }

    private void save() {
        if (tn(nameField.getText()) == null) {
            showError("Name is required.");
            return;
        }
        DirectDeal d = currentDeal();
        onSave.accept(d);
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
