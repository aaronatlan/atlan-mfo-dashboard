package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.enums.BenchmarkStatus;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.scoring.ScoringEngine;
import com.atlan.mfo.ui.util.FormControls;
import javafx.geometry.Pos;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Fiche fonds éditable avec scoring en direct (voir §6.2). Édition ou création. */
public final class FundFormView extends BorderPane {

    private final FundInvestment existing;
    private final ScoringEngine engine;
    private final BiConsumer<FundInvestment, ScoreBreakdown> onSave;

    private final ComboBox<Category> categoryCombo =
            FormControls.enumCombo(Category.values(), Category::label, false);
    private final ComboBox<DealStatus> statusCombo =
            FormControls.enumCombo(DealStatus.values(), DealStatus::label, false);
    private final ComboBox<BenchmarkStatus> benchCombo =
            FormControls.enumCombo(BenchmarkStatus.values(), BenchmarkStatus::label, true);
    private final ComboBox<String> geoCombo = FormControls.geographyCombo();
    private final TextField nameField = FormControls.field("fund name");
    private final TextField assetClassField = FormControls.field("asset class");
    private final TextField commitmentField = FormControls.field("e.g. 25m or 25000000");
    private final DatePicker firstClosePicker = new DatePicker();
    private final DatePicker finalClosePicker = new DatePicker();
    private final TextField contactNameField = FormControls.field("first & last name");
    private final TextField contactEmailField = FormControls.field("email");
    private final TextField contactPhoneField = FormControls.field("phone");
    private final TextArea nextStepsArea = new TextArea();
    private final TextArea commentsArea = new TextArea();
    private final VBox vintageBox = new VBox(6);
    private final List<VintageRow> vintageRows = new ArrayList<>();

    private final ScoreBreakdownView scorePanel = new ScoreBreakdownView();
    private final Label errorLabel = new Label();

    public FundFormView(FundInvestment existing, Category defaultCategory, ScoringEngine engine,
                        BiConsumer<FundInvestment, ScoreBreakdown> onSave, Runnable onCancel) {
        this.existing = existing;
        this.engine = engine;
        this.onSave = onSave;
        getStyleClass().add("form-view");

        setTop(header(existing == null ? "New fund" : "Edit — " + existing.name(), onCancel));
        setCenter(buildBody());
        populate(defaultCategory);
        wireLiveScoring();
        recompute();
    }

    /* ---- Construction ---- */

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
        r = row(g, r, "Category", categoryCombo);
        r = row(g, r, "Name", nameField);
        r = row(g, r, "Status", statusCombo);
        r = row(g, r, "Vs. benchmark", benchCombo);
        r = row(g, r, "Geography", geoCombo);
        r = row(g, r, "Asset class", assetClassField);
        r = row(g, r, "Planned commitment", commitmentField);
        r = row(g, r, "First close", firstClosePicker);
        r = row(g, r, "Final close", finalClosePicker);

        Label contactSection = new Label("CONTACT");
        contactSection.getStyleClass().add("form-section");
        g.add(contactSection, 0, r, 2, 1);
        r++;
        r = row(g, r, "Contact name", contactNameField);
        r = row(g, r, "Contact email", contactEmailField);
        r = row(g, r, "Contact phone", contactPhoneField);

        Label vintTitle = new Label("VINTAGES");
        vintTitle.getStyleClass().add("form-section");
        Button addVintage = new Button("+ Add a vintage");
        addVintage.getStyleClass().add("ghost-button");
        addVintage.setOnAction(e -> {
            addVintageRow(null);
            recompute();
        });

        nextStepsArea.setPromptText("next steps");
        nextStepsArea.setPrefRowCount(2);
        commentsArea.setPromptText("comments");
        commentsArea.setPrefRowCount(3);

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setManaged(false);

        VBox form = new VBox(14, g, vintTitle, vintageHeader(), vintageBox, addVintage,
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

    private HBox vintageHeader() {
        HBox h = new HBox(8);
        h.getStyleClass().add("vintage-head");
        for (String s : new String[]{"Year", "DPI", "TVPI", "IRR", "MOIC", ""}) {
            Label l = new Label(s);
            l.getStyleClass().add("form-section");
            l.setMinWidth(70);
            h.getChildren().add(l);
        }
        return h;
    }

    private VBox labeled(String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        return new VBox(4, l, control);
    }

    private int row(GridPane g, int r, String label, javafx.scene.Node control) {
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

    /* ---- Millésimes ---- */

    private void addVintageRow(FundVintage v) {
        VintageRow vr = new VintageRow(v);
        vintageRows.add(vr);
        vintageBox.getChildren().add(vr.node);
    }

    private final class VintageRow {
        final TextField year = small("yyyy");
        final TextField dpi = small("DPI");
        final TextField tvpi = small("TVPI");
        final TextField irr = small("IRR");
        final TextField moic = small("MOIC");
        HBox node;

        VintageRow(FundVintage v) {
            if (v != null) {
                year.setText(Integer.toString(v.vintageYear()));
                dpi.setText(str(v.dpi()));
                tvpi.setText(str(v.tvpi()));
                irr.setText(str(v.irr()));
                moic.setText(str(v.moic()));
            }
            Button remove = new Button("✕");
            remove.getStyleClass().add("ghost-button");
            remove.setOnAction(e -> {
                vintageRows.remove(this);
                vintageBox.getChildren().remove(node);
                recompute();
            });
            for (TextField f : new TextField[]{year, dpi, tvpi, irr, moic}) {
                f.textProperty().addListener((o, a, b) -> recompute());
            }
            node = new HBox(8, year, dpi, tvpi, irr, moic, remove);
        }

        FundVintage toModel() {
            Integer y = parseInt(year.getText());
            if (y == null) {
                return null;
            }
            return new FundVintage(0, 0, y,
                    FormControls.parse(dpi.getText()), FormControls.parse(tvpi.getText()),
                    FormControls.parse(irr.getText()), FormControls.parse(moic.getText()));
        }

        private TextField small(String prompt) {
            TextField t = FormControls.field(prompt);
            t.setMinWidth(70);
            t.setPrefWidth(70);
            return t;
        }
    }

    /* ---- Données ---- */

    private void populate(Category defaultCategory) {
        statusCombo.setValue(DealStatus.INITIAL_REVIEW);
        categoryCombo.setValue(defaultCategory != null ? defaultCategory : Category.BUYOUT_GROWTH_VC);
        if (existing != null) {
            categoryCombo.setValue(existing.category());
            nameField.setText(existing.name());
            statusCombo.setValue(existing.status());
            benchCombo.setValue(existing.vsBenchmark());
            geoCombo.setValue(existing.geography());
            assetClassField.setText(existing.assetClass());
            commitmentField.setText(str(existing.commitment()));
            firstClosePicker.setValue(existing.firstClose());
            finalClosePicker.setValue(existing.finalClose());
            contactNameField.setText(existing.contactName());
            contactEmailField.setText(existing.contactEmail());
            contactPhoneField.setText(existing.contactPhone());
            nextStepsArea.setText(existing.nextSteps());
            commentsArea.setText(existing.comments());
            existing.vintages().forEach(this::addVintageRow);
        }
    }

    private void wireLiveScoring() {
        categoryCombo.valueProperty().addListener((o, a, b) -> recompute());
        geoCombo.valueProperty().addListener((o, a, b) -> recompute());
        finalClosePicker.valueProperty().addListener((o, a, b) -> recompute());
    }

    private void recompute() {
        scorePanel.update(engine.score(currentFund(), LocalDate.now()));
    }

    private FundInvestment currentFund() {
        List<FundVintage> vs = new ArrayList<>();
        for (VintageRow vr : vintageRows) {
            FundVintage m = vr.toModel();
            if (m != null) {
                vs.add(m);
            }
        }
        long id = existing != null ? existing.id() : 0;
        long version = existing != null ? existing.version() : 0;
        return new FundInvestment(
                id,
                categoryCombo.getValue(),
                tn(nameField.getText()),
                tn(nextStepsArea.getText()),
                statusCombo.getValue(),
                benchCombo.getValue(),
                geoCombo.getValue(),
                tn(assetClassField.getText()),
                FormControls.parse(commitmentField.getText()),
                vs,
                firstClosePicker.getValue(),
                finalClosePicker.getValue(),
                tn(commentsArea.getText()),
                null, null, null, null, null, null,
                version, null, null,
                tn(contactNameField.getText()),
                tn(contactEmailField.getText()),
                tn(contactPhoneField.getText()));
    }

    private void save() {
        if (tn(nameField.getText()) == null) {
            showError("Name is required.");
            return;
        }
        FundInvestment f = currentFund();
        onSave.accept(f, engine.score(f, LocalDate.now()));
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    /* ---- Helpers ---- */

    private static String tn(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String str(Double d) {
        return d == null ? "" : (d == Math.rint(d) ? Long.toString((long) (double) d) : Double.toString(d));
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
