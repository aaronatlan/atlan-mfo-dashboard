package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.ScoreComponent;
import com.atlan.mfo.ui.util.Formatters;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Fiche détail en lecture seule : cartes sur deux colonnes, score en barres de
 * progression. Le score affiché est recalculé en direct (§13.4).
 */
public final class DetailView extends BorderPane {

    private DetailView() {
        getStyleClass().add("detail-view");
    }

    /** Fiche fonds en lecture seule (sans éditer/supprimer), pour le mode présentation. */
    public static DetailView ofFundReadOnly(FundInvestment f, ScoreBreakdown breakdown, Runnable onBack) {
        return ofFund(f, breakdown, onBack, null, null);
    }

    /** Fiche deal en lecture seule (sans éditer/supprimer), pour le mode présentation. */
    public static DetailView ofDealReadOnly(DirectDeal d, ScoreBreakdown breakdown, Runnable onBack) {
        return ofDeal(d, breakdown, onBack, null, null);
    }

    public static DetailView ofFund(FundInvestment f, ScoreBreakdown breakdown,
                                    Runnable onBack, Runnable onEdit, Runnable onDelete) {
        DetailView v = new DetailView();
        v.setTop(v.header(f.name(), f.category().label() + "  ·  " + f.status().label(),
                breakdown, onBack, onEdit, onDelete));

        GridPane g1 = grid();
        addRow(g1, "Géographie", Formatters.text(f.geography()));
        addRow(g1, "Classe d'actifs", Formatters.text(f.assetClass()));
        addRow(g1, "Vs. benchmark", f.vsBenchmark() == null ? "—" : f.vsBenchmark().label());
        addRow(g1, "Capital envisagé", Formatters.money(f.commitment()));
        addRow(g1, "Prochaines étapes", Formatters.text(f.nextSteps()));

        GridPane g4 = grid();
        addRow(g4, "First close", Formatters.date(f.firstClose()));
        addRow(g4, "Final close", Formatters.date(f.finalClose()));

        GridPane layout = twoColumns();
        layout.add(card("Détail du score", scoreBars(breakdown)), 0, 0);
        layout.add(card("Général", g1), 1, 0);
        layout.add(card("Millésimes (track record)", vintageTable(f.vintages())), 0, 1);
        layout.add(card("Timeline", g4), 1, 1);
        if (f.comments() != null && !f.comments().isBlank()) {
            Node comments = card("Commentaires", paragraph(f.comments()));
            layout.add(comments, 0, 2);
            GridPane.setColumnSpan(comments, 2);
        }

        v.setCenter(scroll(layout));
        return v;
    }

    public static DetailView ofDeal(DirectDeal d, ScoreBreakdown breakdown,
                                    Runnable onBack, Runnable onEdit, Runnable onDelete) {
        DetailView v = new DetailView();
        v.setTop(v.header(d.name(), PipelineItem.DEALS_STRATEGY + "  ·  " + d.status().label(),
                breakdown, onBack, onEdit, onDelete));

        GridPane g1 = grid();
        addRow(g1, "Secteur", Formatters.text(d.industry()));
        addRow(g1, "GP / sponsor", Formatters.text(d.gp()));
        addRow(g1, "Géographie", Formatters.text(d.geography()));
        addRow(g1, "Type d'investissement", Formatters.text(d.invType()));
        addRow(g1, "Vs. benchmark", d.vsBenchmark() == null ? "—" : d.vsBenchmark().label());
        addRow(g1, "Capital envisagé", Formatters.money(d.commitment()));
        addRow(g1, "Prochaines étapes", Formatters.text(d.nextSteps()));

        GridPane g2 = grid();
        addRow(g2, "Revenue", Formatters.money(d.revenue()));
        addRow(g2, "Revenue CAGR", Formatters.percent(d.cagrPct()));
        addRow(g2, "EBITDA", Formatters.money(d.ebitda()));
        addRow(g2, "Croissance EBITDA", Formatters.percent(d.ebitdaGrPct()));
        addRow(g2, "Marge EBITDA", Formatters.percent(d.ebitdaMgnPct()));
        addRow(g2, "FCF", Formatters.money(d.fcf()));
        addRow(g2, "Conversion FCF", Formatters.percent(d.fcfConvPct()));
        addRow(g2, "EV", Formatters.money(d.ev()));

        GridPane g3 = grid();
        addRow(g3, "Multiple d'entrée", Formatters.multiple(d.entryMult()));
        addRow(g3, "Multiples comparables", Formatters.text(d.peersMult()));
        addRow(g3, "Valeur de sortie", Formatters.money(d.exitVal()));
        addRow(g3, "IRR attendu", Formatters.percent(d.expIrrPct()));
        addRow(g3, "MOIC attendu", Formatters.multiple(d.expMoic()));

        GridPane g4 = grid();
        addRow(g4, "Deadline", Formatters.date(d.dealDeadline()));
        addRow(g4, "Sortie cible", Formatters.date(d.targetExit()));

        GridPane layout = twoColumns();
        layout.add(card("Détail du score", scoreBars(breakdown)), 0, 0);
        layout.add(card("Général", g1), 1, 0);
        layout.add(card("Performance financière", g2), 0, 1);
        layout.add(card("Retours attendus", g3), 1, 1);
        layout.add(card("Timeline", g4), 0, 2);
        if (d.comments() != null && !d.comments().isBlank()) {
            layout.add(card("Commentaires", paragraph(d.comments())), 1, 2);
        }

        v.setCenter(scroll(layout));
        return v;
    }

    /* ---- En-tête ---- */

    private HBox header(String name, String subtitle, ScoreBreakdown breakdown,
                        Runnable onBack, Runnable onEdit, Runnable onDelete) {
        Button back = new Button("← Retour");
        back.getStyleClass().add("ghost-button");
        back.setOnAction(e -> onBack.run());

        Label title = new Label(name);
        title.getStyleClass().add("detail-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("detail-subtitle");
        VBox titles = new VBox(2, title, sub);

        VBox scoreBox = new VBox(2);
        scoreBox.setAlignment(Pos.CENTER_RIGHT);
        Label scoreVal = new Label(Formatters.score(breakdown.score()));
        scoreVal.getStyleClass().add("detail-score");
        scoreBox.getChildren().addAll(scoreVal, OpportunityTable.tierNode(breakdown.tier()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(16, back, titles, spacer);
        // Boutons d'action optionnels : absents en lecture seule (mode présentation).
        if (onDelete != null) {
            Button delete = new Button("Supprimer");
            delete.getStyleClass().add("danger-button");
            delete.setOnAction(e -> onDelete.run());
            bar.getChildren().add(delete);
        }
        if (onEdit != null) {
            Button edit = new Button("Éditer");
            edit.getStyleClass().add("primary-button");
            edit.setOnAction(e -> onEdit.run());
            bar.getChildren().add(edit);
        }
        bar.getChildren().add(scoreBox);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("detail-header");
        return bar;
    }

    /* ---- Score en barres de progression ---- */

    private static Node scoreBars(ScoreBreakdown b) {
        VBox rows = new VBox(12);
        for (ScoreComponent c : b.components()) {
            Label name = new Label(c.label());
            name.getStyleClass().add("score-bar-label");
            name.setMinWidth(120);

            Region fill = new Region();
            fill.getStyleClass().add("score-bar-fill");
            StackPane track = new StackPane(fill);
            track.getStyleClass().add("score-bar-track");
            track.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(track, Priority.ALWAYS);
            double fraction = c.communicated() && c.maxPoints() > 0 ? c.subScore() / c.maxPoints() : 0;
            fill.maxWidthProperty().bind(track.widthProperty().multiply(fraction));

            Label value = new Label(c.communicated()
                    ? String.format("%.1f / %.0f", c.subScore(), c.maxPoints())
                    : "exclu");
            value.getStyleClass().add("score-bar-value");
            value.setMinWidth(74);
            value.setAlignment(Pos.CENTER_RIGHT);

            HBox row = new HBox(12, name, track, value);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }

        Label totalLabel = new Label("Total");
        totalLabel.getStyleClass().add("score-bar-total");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label totalValue = new Label(b.score() + "  ·  " + b.tier().label());
        totalValue.getStyleClass().add("score-bar-total");
        HBox total = new HBox(totalLabel, spacer, totalValue);
        total.getStyleClass().add("score-bar-total-row");

        rows.getChildren().add(total);
        return rows;
    }

    /* ---- Cartes et mise en page ---- */

    private static GridPane twoColumns() {
        GridPane g = new GridPane();
        g.getStyleClass().add("detail-layout");
        g.setHgap(16);
        g.setVgap(16);
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        half.setHalignment(HPos.LEFT);
        g.getColumnConstraints().addAll(half, half);
        return g;
    }

    private static VBox card(String title, Node content) {
        Label t = new Label(title.toUpperCase());
        t.getStyleClass().add("detail-card-title");
        VBox box = new VBox(14, t, content);
        box.getStyleClass().add("detail-card");
        box.setMaxWidth(Double.MAX_VALUE);
        box.setMaxHeight(Double.MAX_VALUE);
        return box;
    }

    private static ScrollPane scroll(Node body) {
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("detail-scroll");
        return sp;
    }

    private static Label paragraph(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("detail-paragraph");
        l.setWrapText(true);
        return l;
    }

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.getStyleClass().add("detail-grid");
        g.setHgap(24);
        g.setVgap(10);
        return g;
    }

    private static void addRow(GridPane g, String key, String value) {
        int r = g.getChildren().size() / 2;   // 2 cellules (clé, valeur) par ligne
        Label k = new Label(key);
        k.getStyleClass().add("detail-key");
        k.setMinWidth(150);
        Label v = new Label(value);
        v.getStyleClass().add("detail-value");
        v.setWrapText(true);
        g.add(k, 0, r);
        g.add(v, 1, r);
    }

    /** Tableau des millésimes (plus récent en haut), ou message si aucun. */
    private static Node vintageTable(List<FundVintage> vintages) {
        if (vintages == null || vintages.isEmpty()) {
            return paragraph("Aucun millésime communiqué.");
        }
        GridPane g = new GridPane();
        g.getStyleClass().add("method-table");
        g.setHgap(28);
        g.setVgap(8);
        String[] heads = {"Millésime", "DPI", "TVPI", "IRR", "MOIC"};
        for (int c = 0; c < heads.length; c++) {
            Label h = new Label(heads[c]);
            h.getStyleClass().add("method-head");
            g.add(h, c, 0);
        }
        int r = 1;
        for (FundVintage v : vintages) {
            String[] cells = {
                    Integer.toString(v.vintageYear()),
                    Formatters.multiple(v.dpi()),
                    Formatters.multiple(v.tvpi()),
                    Formatters.percent(v.irr()),
                    Formatters.multiple(v.moic())};
            for (int c = 0; c < cells.length; c++) {
                Label cell = new Label(cells[c]);
                cell.getStyleClass().add("method-cell");
                g.add(cell, c, r);
            }
            r++;
        }
        return g;
    }
}
