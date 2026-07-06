package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.model.enums.Tier;
import com.atlan.mfo.ui.util.Formatters;
import com.atlan.mfo.ui.util.FormControls;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Mode présentation : épuré, lecture seule, pensé pour la projection en comité
 * d'investissement (voir §6.3). Pas de menu latéral ni de filtres.
 */
public final class PresentationView extends BorderPane {

    private static final String GOVERNANCE =
            "Le score est un support de décision. Le comité d'investissement conserve l'entière autorité ; "
                    + "une revue humaine est requise à tous les niveaux.";

    private final BiConsumer<PipelineItem, DealStatus> onStatusChange;

    /**
     * @param onStatusChange appelé quand un statut est changé (analyste) ; {@code null}
     *                       pour un partner (statuts en lecture seule).
     */
    public PresentationView(List<PipelineItem> items, BiConsumer<PipelineItem, DealStatus> onStatusChange,
                            Runnable onExitToAnalyst, Runnable onToggleFullScreen, Runnable onLogout) {
        getStyleClass().add("presentation-root");
        this.onStatusChange = onStatusChange;

        List<PipelineItem> active = items.stream().filter(PipelineItem::isActive).toList();

        setTop(topBar(onExitToAnalyst, onToggleFullScreen, onLogout));
        setCenter(scroll(body(active, items)));
        setBottom(governance());
    }

    private static ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("pres-scroll");
        return sp;
    }

    /* ---- Barre de contrôle ---- */

    private HBox topBar(Runnable onExitToAnalyst, Runnable onToggleFullScreen, Runnable onLogout) {
        Label brand = new Label("ATLAN");
        brand.getStyleClass().add("pres-brand");
        Label sub = new Label("COMITÉ D'INVESTISSEMENT");
        sub.getStyleClass().add("pres-brand-sub");
        HBox left = new HBox(10, brand, sub);
        left.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_RIGHT);
        Button fullscreen = new Button("Plein écran");
        fullscreen.getStyleClass().add("ghost-button");
        fullscreen.setOnAction(e -> onToggleFullScreen.run());
        controls.getChildren().add(fullscreen);
        if (onExitToAnalyst != null) {
            Button analyst = new Button("Vue analyste");
            analyst.getStyleClass().add("ghost-button");
            analyst.setOnAction(e -> onExitToAnalyst.run());
            controls.getChildren().add(analyst);
        }
        Button logout = new Button("Déconnexion");
        logout.getStyleClass().add("ghost-button");
        logout.setOnAction(e -> onLogout.run());
        controls.getChildren().add(logout);

        HBox bar = new HBox(left, spacer, controls);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("pres-topbar");
        return bar;
    }

    /* ---- Corps ---- */

    private VBox body(List<PipelineItem> active, List<PipelineItem> all) {
        double capital = active.stream().map(PipelineItem::commitment)
                .filter(c -> c != null).mapToDouble(Double::doubleValue).sum();
        var avg = active.stream().map(PipelineItem::score)
                .filter(s -> s != null).mapToInt(Integer::intValue).average();
        long strong = active.stream().filter(i -> i.tier() == Tier.STRONG).count();

        Label heroLabel = new Label("CAPITAL EN REVUE");
        heroLabel.getStyleClass().add("pres-hero-label");
        Label heroValue = new Label(Formatters.money(capital));
        heroValue.getStyleClass().add("pres-hero-value");
        VBox hero = new VBox(2, heroLabel, heroValue);

        HBox metrics = new HBox(40,
                metric(Long.toString(active.size()), "OPPORTUNITÉS ACTIVES"),
                metric(avg.isPresent() ? Long.toString(Math.round(avg.getAsDouble())) : "—", "SCORE MOYEN"),
                metric(Long.toString(strong), "TIER STRONG"));

        VBox box = new VBox(28, hero, metrics, allocation(active), decisions(all));
        box.getStyleClass().add("presentation-body");
        return box;
    }

    private VBox metric(String value, String label) {
        Label v = new Label(value);
        v.getStyleClass().add("pres-metric-value");
        Label l = new Label(label);
        l.getStyleClass().add("pres-metric-label");
        return new VBox(2, v, l);
    }

    /* ---- Allocation par stratégie (barres) ---- */

    private VBox allocation(List<PipelineItem> active) {
        Map<String, Double> byStrategy = new LinkedHashMap<>();
        byStrategy.put(Category.BUYOUT_GROWTH_VC.label(), 0d);
        byStrategy.put(Category.SECONDARIES.label(), 0d);
        byStrategy.put(Category.PRIVATE_CREDIT.label(), 0d);
        byStrategy.put(PipelineItem.DEALS_STRATEGY, 0d);
        for (PipelineItem i : active) {
            if (i.commitment() != null) {
                byStrategy.merge(i.strategy(), i.commitment(), Double::sum);
            }
        }
        double max = byStrategy.values().stream().mapToDouble(Double::doubleValue).max().orElse(0d);

        Label title = new Label("ALLOCATION PAR STRATÉGIE");
        title.getStyleClass().add("pres-section-title");
        VBox rows = new VBox(10);
        byStrategy.forEach((name, value) -> rows.getChildren().add(bar(name, value, max)));

        VBox box = new VBox(14, title, rows);
        return box;
    }

    private HBox bar(String name, double value, double max) {
        Label label = new Label(name);
        label.getStyleClass().add("bar-label");
        label.setMinWidth(210);

        Region fill = new Region();
        fill.getStyleClass().add("bar-fill");
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("bar-track");
        track.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(track, Priority.ALWAYS);
        double fraction = max > 0 ? value / max : 0;
        fill.maxWidthProperty().bind(track.widthProperty().multiply(fraction));

        Label amount = new Label(Formatters.money(value));
        amount.getStyleClass().add("bar-value");
        amount.setMinWidth(90);

        HBox row = new HBox(14, label, track, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /* ---- Opportunités : liste de décision (statut modifiable en comité) ---- */

    private VBox decisions(List<PipelineItem> all) {
        List<PipelineItem> sorted = all.stream()
                .filter(i -> i.score() != null)
                .sorted(Comparator.comparingInt(PipelineItem::score).reversed())
                .toList();

        Label title = new Label(onStatusChange != null
                ? "OPPORTUNITÉS — DÉCISIONS DE STATUT" : "OPPORTUNITÉS");
        title.getStyleClass().add("pres-section-title");
        VBox rows = new VBox(8);
        for (PipelineItem i : sorted) {
            rows.getChildren().add(decisionRow(i));
        }
        return new VBox(14, title, rows);
    }

    private HBox decisionRow(PipelineItem i) {
        Label name = new Label(i.name());
        name.getStyleClass().add("pres-priority-name");
        name.setMinWidth(240);
        Label strat = new Label(i.strategy());
        strat.getStyleClass().add("pres-priority-strategy");
        strat.setMinWidth(200);

        Node statusNode;
        if (onStatusChange != null) {
            ComboBox<DealStatus> combo = FormControls.enumCombo(DealStatus.values(), DealStatus::label, false);
            combo.setValue(i.status());
            combo.getStyleClass().add("pres-status-combo");
            combo.valueProperty().addListener((o, a, b) -> {
                if (b != null && b != i.status()) {
                    onStatusChange.accept(i, b);
                }
            });
            statusNode = combo;
        } else {
            Label s = new Label(i.status().label());
            s.getStyleClass().add("pres-priority-strategy");
            statusNode = s;
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label score = new Label(Formatters.score(i.score()));
        score.getStyleClass().add("pres-priority-score");

        HBox row = new HBox(16, name, strat, spacer, statusNode, score, OpportunityTable.tierNode(i.tier()));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("pres-priority-row");
        return row;
    }

    /* ---- Gouvernance ---- */

    private Label governance() {
        Label g = new Label(GOVERNANCE);
        g.getStyleClass().add("pres-governance");
        g.setWrapText(true);
        return g;
    }
}
