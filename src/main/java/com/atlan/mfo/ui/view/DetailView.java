package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.Tier;
import com.atlan.mfo.ui.util.Formatters;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Fiche détail en lecture seule (Phase 1). L'édition arrive en Phase 3.
 * Le score affiché reste le {@code score_snapshot} ; le recalcul en direct
 * (§13.4) est branché en Phase 2.
 */
public final class DetailView extends BorderPane {

    private DetailView() {
        getStyleClass().add("detail-view");
    }

    public static DetailView ofFund(FundInvestment f, Runnable onBack) {
        DetailView v = new DetailView();
        v.setTop(v.header(f.name(), f.category().label() + "  ·  " + f.status().label(),
                f.scoreSnapshot(), onBack));

        VBox body = new VBox(18);
        body.getStyleClass().add("detail-body");

        GridPane g1 = grid();
        addRow(g1, "Géographie", Formatters.text(f.geography()));
        addRow(g1, "Classe d'actifs", Formatters.text(f.assetClass()));
        addRow(g1, "Vs. benchmark", f.vsBenchmark() == null ? "—" : f.vsBenchmark().label());
        addRow(g1, "Capital envisagé", Formatters.money(f.commitment()));
        addRow(g1, "Prochaines étapes", Formatters.text(f.nextSteps()));

        Node vintages = vintageTable(f.vintages());

        GridPane g4 = grid();
        addRow(g4, "First close", Formatters.date(f.firstClose()));
        addRow(g4, "Final close", Formatters.date(f.finalClose()));

        body.getChildren().addAll(
                section("Général"), g1,
                section("Millésimes (track record)"), vintages,
                section("Timeline"), g4);
        if (f.comments() != null && !f.comments().isBlank()) {
            body.getChildren().addAll(section("Commentaires"), paragraph(f.comments()));
        }

        v.setCenter(scroll(body));
        return v;
    }

    public static DetailView ofDeal(DirectDeal d, Runnable onBack) {
        DetailView v = new DetailView();
        v.setTop(v.header(d.name(), PipelineItem.DEALS_STRATEGY + "  ·  " + d.status().label(),
                d.scoreSnapshot(), onBack));

        VBox body = new VBox(18);
        body.getStyleClass().add("detail-body");

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

        body.getChildren().addAll(
                section("Général"), g1,
                section("Performance financière"), g2,
                section("Retours attendus"), g3,
                section("Timeline"), g4);
        if (d.comments() != null && !d.comments().isBlank()) {
            body.getChildren().addAll(section("Commentaires"), paragraph(d.comments()));
        }

        v.setCenter(scroll(body));
        return v;
    }

    private HBox header(String name, String subtitle, Integer score, Runnable onBack) {
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
        Label scoreVal = new Label(Formatters.score(score));
        scoreVal.getStyleClass().add("detail-score");
        scoreBox.getChildren().add(scoreVal);
        if (score != null) {
            scoreBox.getChildren().add(OpportunityTable.tierNode(Tier.fromScore(score)));
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox bar = new HBox(16, back, titles, spacer, scoreBox);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("detail-header");
        return bar;
    }

    private static ScrollPane scroll(VBox body) {
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("detail-scroll");
        return sp;
    }

    private static Label section(String title) {
        Label l = new Label(title.toUpperCase());
        l.getStyleClass().add("detail-section");
        return l;
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
        g.setVgap(8);
        return g;
    }

    /** Tableau des millésimes (plus récent en haut), ou message si aucun. */
    private static Node vintageTable(List<FundVintage> vintages) {
        if (vintages == null || vintages.isEmpty()) {
            return paragraph("Aucun millésime communiqué.");
        }
        GridPane g = new GridPane();
        g.getStyleClass().add("method-table");
        g.setHgap(28);
        g.setVgap(6);
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

    private static void addRow(GridPane g, String key, String value) {
        int r = g.getChildren().size() / 2;   // 2 cellules (clé, valeur) par ligne
        Label k = new Label(key);
        k.getStyleClass().add("detail-key");
        Label v = new Label(value);
        v.getStyleClass().add("detail-value");
        g.add(k, 0, r);
        g.add(v, 1, r);
    }
}
