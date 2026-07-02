package com.atlan.mfo.ui.view;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Page de référence : grilles de scoring, normalisation et tiers (voir §6.1, §5). */
public final class MethodologyView extends ScrollPane {

    public MethodologyView() {
        getStyleClass().add("detail-scroll");
        setFitToWidth(true);

        VBox body = new VBox(18);
        body.getStyleClass().add("detail-body");

        body.getChildren().addAll(
                title("Méthodologie de scoring"),
                paragraph("Le score est un support de décision. Le comité d'investissement conserve "
                        + "l'entière autorité ; une revue humaine est requise à tous les niveaux."),

                section("Formule de normalisation (commune aux 3 grilles)"),
                paragraph("Earned = somme des sous-scores des métriques communiquées.\n"
                        + "Possible = somme des points max des métriques communiquées.\n"
                        + "Score = MIN( Earned / MAX(Possible, 80) × 100 , 95 ).\n\n"
                        + "Une métrique non communiquée est exclue du calcul (jamais scorée à 0). "
                        + "Plancher du dénominateur à 80, plafond du score à 95."),

                section("Grille A — Buyout, growth, VC (et Secondaries)"),
                gridTable(new String[][]{
                        {"Composant", "Points", "Cible"},
                        {"DPI", "30", "0,8x"},
                        {"IRR", "25", "0,30"},
                        {"MOIC", "20", "2,5x"},
                        {"Géographie", "15", "US / EU / UK / DACH"},
                        {"Timeline", "10", "proximité final close"}}),

                section("Grille B — Private credit"),
                gridTable(new String[][]{
                        {"Composant", "Points", "Cible"},
                        {"DPI", "30", "0,7x"},
                        {"IRR", "25", "0,20"},
                        {"MOIC", "20", "1,8x"},
                        {"Géographie", "15", "US / EU / UK / DACH"},
                        {"Timeline", "10", "proximité final close"}}),

                section("Grille C — Co-investissement et direct"),
                gridTable(new String[][]{
                        {"Composant", "Points", "Cible"},
                        {"Revenue CAGR", "25", "0,40"},
                        {"EBITDA Margin", "20", "0,35"},
                        {"FCF Conversion", "10", "0,90"},
                        {"Expected IRR", "25", "0,30"},
                        {"Géographie", "10", "US / EU / UK"},
                        {"Timeline", "10", "proximité deadline"}}),

                section("Tiers"),
                gridTable(new String[][]{
                        {"Score", "Tier", "Action"},
                        {"70 – 95", "Strong", "Vote IC"},
                        {"40 – 69", "Moderate", "DD approfondie"},
                        {"0 – 39", "Caution", "Décliner / retravailler"}}));

        setContent(body);
    }

    private static Label title(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("detail-title");
        return l;
    }

    private static Label section(String t) {
        Label l = new Label(t.toUpperCase());
        l.getStyleClass().add("detail-section");
        return l;
    }

    private static Label paragraph(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("detail-paragraph");
        l.setWrapText(true);
        return l;
    }

    private static GridPane gridTable(String[][] rows) {
        GridPane g = new GridPane();
        g.getStyleClass().add("method-table");
        g.setHgap(28);
        g.setVgap(6);
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                Label cell = new Label(rows[r][c]);
                cell.getStyleClass().add(r == 0 ? "method-head" : "method-cell");
                g.add(cell, c, r);
            }
        }
        return g;
    }
}
