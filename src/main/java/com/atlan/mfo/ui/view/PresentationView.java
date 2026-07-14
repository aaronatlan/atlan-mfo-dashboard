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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Mode présentation : épuré, pensé pour la projection en comité d'investissement
 * (voir §6.3). Pas de menu latéral. Un filtre par stratégie et le clic sur une
 * opportunité (→ fiche complète) sont disponibles.
 */
public final class PresentationView extends BorderPane {

    private static final String ALL_STRATEGIES = "All strategies";

    private final BiConsumer<PipelineItem, DealStatus> onStatusChange;
    private final Consumer<PipelineItem> onOpen;
    private final Function<PipelineItem, String> headline;

    /**
     * @param onStatusChange appelé quand un statut est changé (analyste) ; {@code null}
     *                       pour un partner (statuts en lecture seule)
     * @param onOpen         ouvre la fiche complète d'une opportunité (clic sur le nom)
     * @param headline       texte résumé (millésime le plus récent / métriques) par ligne
     */
    public PresentationView(List<PipelineItem> items, BiConsumer<PipelineItem, DealStatus> onStatusChange,
                            Consumer<PipelineItem> onOpen, Function<PipelineItem, String> headline,
                            Runnable onExitToAnalyst, Runnable onToggleFullScreen, Runnable onLogout) {
        getStyleClass().add("presentation-root");
        this.onStatusChange = onStatusChange;
        this.onOpen = onOpen;
        this.headline = headline;

        List<PipelineItem> active = items.stream().filter(PipelineItem::isActive).toList();

        setTop(topBar(onExitToAnalyst, onToggleFullScreen, onLogout));
        setCenter(scroll(body(active, items)));
    }

    private static ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("pres-scroll");
        return sp;
    }

    /* ---- Barre de contrôle ---- */

    private HBox topBar(Runnable onExitToAnalyst, Runnable onToggleFullScreen, Runnable onLogout) {
        Label brand = new Label("PATRIMIUM");
        brand.getStyleClass().add("pres-brand");
        Label sub = new Label("INVESTMENT COMMITTEE");
        sub.getStyleClass().add("pres-brand-sub");
        HBox left = new HBox(10, brand, sub);
        left.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_RIGHT);
        Button fullscreen = new Button("Full screen");
        fullscreen.getStyleClass().add("ghost-button");
        fullscreen.setOnAction(e -> onToggleFullScreen.run());
        controls.getChildren().add(fullscreen);
        if (onExitToAnalyst != null) {
            Button analyst = new Button("Analyst view");
            analyst.getStyleClass().add("ghost-button");
            analyst.setOnAction(e -> onExitToAnalyst.run());
            controls.getChildren().add(analyst);
        }
        Button logout = new Button("Log out");
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
        double capital = active.stream().map(PipelineItem::commitmentUsd)
                .filter(c -> c != null).mapToDouble(Double::doubleValue).sum();
        var avg = active.stream().map(PipelineItem::score)
                .filter(s -> s != null).mapToInt(Integer::intValue).average();
        long strong = active.stream().filter(i -> i.tier() == Tier.STRONG).count();

        Label heroLabel = new Label("CAPITAL UNDER REVIEW");
        heroLabel.getStyleClass().add("pres-hero-label");
        Label heroValue = new Label(Formatters.money(capital, "USD"));
        heroValue.getStyleClass().add("pres-hero-value");
        VBox hero = new VBox(2, heroLabel, heroValue);

        HBox metrics = new HBox(40,
                metric(Long.toString(active.size()), "ACTIVE OPPORTUNITIES"),
                metric(avg.isPresent() ? Long.toString(Math.round(avg.getAsDouble())) : "—", "AVERAGE SCORE"),
                metric(Long.toString(strong), "STRONG TIER"));

        VBox box = new VBox(28, hero, metrics, panelsRow(active, all),
                panel("GEOGRAPHIC EXPOSURE — BY OPPORTUNITY COUNT", geographyChart(active)),
                decisions(all));
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

    /* ---- Panneaux graphiques (allocation + pipeline) côte à côte ---- */

    private HBox panelsRow(List<PipelineItem> active, List<PipelineItem> all) {
        VBox allocation = panel("ALLOCATION BY STRATEGY", allocationChart(active));
        VBox pipeline = panel("PIPELINE BY STAGE", statusFunnel(all));
        allocation.setMaxWidth(Double.MAX_VALUE);
        pipeline.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(allocation, Priority.ALWAYS);
        HBox.setHgrow(pipeline, Priority.ALWAYS);
        HBox row = new HBox(20, allocation, pipeline);
        row.setFillHeight(true);
        return row;
    }

    private VBox panel(String titleText, Node content) {
        Label title = new Label(titleText);
        title.getStyleClass().add("pres-section-title");
        VBox box = new VBox(18, title, content);
        box.getStyleClass().add("pres-panel");
        return box;
    }

    /* ---- Allocation par stratégie : donut (Arc natif) + légende ---- */

    private static final String[] SEG_LABELS = {
            Category.BUYOUT_GROWTH_VC.label(), Category.SECONDARIES.label(),
            Category.PRIVATE_CREDIT.label(), PipelineItem.DEALS_STRATEGY};

    private HBox allocationChart(List<PipelineItem> active) {
        double[] values = new double[SEG_LABELS.length];
        for (PipelineItem i : active) {
            if (i.commitmentUsd() == null) {
                continue;
            }
            for (int k = 0; k < SEG_LABELS.length; k++) {
                if (SEG_LABELS[k].equals(i.strategy())) {
                    values[k] += i.commitmentUsd();
                    break;
                }
            }
        }
        double total = 0;
        for (double v : values) {
            total += v;
        }

        // Anneau : secteurs pleins (Arc ROUND depuis le centre) + un disque central couleur
        // carte qui « perce » le trou. Rendu net, sans artefact de contour.
        double rOuter = 92, rInner = 56, cx = 92, cy = 92;
        Pane ring = new Pane();
        ring.setMinSize(184, 184);
        ring.setPrefSize(184, 184);
        ring.setMaxSize(184, 184);
        java.util.List<Arc> arcs = new java.util.ArrayList<>();
        java.util.List<Integer> arcSeg = new java.util.ArrayList<>();
        double startAngle = 90; // haut ; les secteurs tournent dans le sens horaire (longueur négative)
        for (int k = 0; k < values.length; k++) {
            double frac = total > 0 ? values[k] / total : 0;
            if (frac <= 0) {
                continue;
            }
            double sweep = -frac * 360;
            Arc arc = new Arc(cx, cy, rOuter, rOuter, startAngle, sweep);
            arc.setType(ArcType.ROUND);
            arc.getStyleClass().addAll("donut-seg", "donut-seg-" + k);
            ring.getChildren().add(arc);
            arcs.add(arc);
            arcSeg.add(k);
            startAngle += sweep;
        }
        Circle hole = new Circle(cx, cy, rInner);
        hole.getStyleClass().add("donut-hole");
        ring.getChildren().add(hole);
        // Le nombre seul tient dans le trou ; la devise passe dans le sous-libellé.
        Label totalValue = new Label(Formatters.money(total));
        totalValue.getStyleClass().add("donut-total-value");
        Label totalCap = new Label("USD COMMITTED");
        totalCap.getStyleClass().add("donut-total-label");
        VBox center = new VBox(1, totalValue, totalCap);
        center.setAlignment(Pos.CENTER);
        center.setMouseTransparent(true);   // laisse passer le survol vers les secteurs
        StackPane donut = new StackPane(ring, center);
        donut.setMinSize(180, 180);

        VBox legend = new VBox(12);
        legend.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(legend, Priority.ALWAYS);
        HBox[] legendRows = new HBox[SEG_LABELS.length];
        for (int k = 0; k < SEG_LABELS.length; k++) {
            legendRows[k] = legendRow(k, SEG_LABELS[k], values[k], total);
            legend.getChildren().add(legendRows[k]);
        }

        // Interactivité : survol d'un secteur → surbrillance (les autres s'atténuent),
        // centre = montant + part, ligne de légende mise en avant, et tooltip précis.
        final double totalF = total;
        for (int a = 0; a < arcs.size(); a++) {
            Arc arc = arcs.get(a);
            int k = arcSeg.get(a);
            long pct = totalF > 0 ? Math.round(values[k] / totalF * 100) : 0;
            arc.setCursor(javafx.scene.Cursor.HAND);
            Tooltip tip = new Tooltip(SEG_LABELS[k] + ":  " + Formatters.money(values[k], "USD") + "  ·  " + pct + "%");
            tip.setShowDelay(javafx.util.Duration.millis(120));
            Tooltip.install(arc, tip);
            arc.setOnMouseEntered(e -> {
                for (Arc other : arcs) {
                    other.setOpacity(other == arc ? 1.0 : 0.28);
                }
                totalValue.setText(Formatters.money(values[k]));
                totalCap.setText(pct + "% OF TOTAL");
                legendRows[k].getStyleClass().add("legend-row-active");
            });
            arc.setOnMouseExited(e -> {
                for (Arc other : arcs) {
                    other.setOpacity(1.0);
                }
                totalValue.setText(Formatters.money(totalF));
                totalCap.setText("USD COMMITTED");
                legendRows[k].getStyleClass().remove("legend-row-active");
            });
        }

        HBox row = new HBox(28, donut, legend);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox legendRow(int idx, String name, double value, double total) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("legend-swatch", "swatch-" + idx);
        Label label = new Label(name);
        label.getStyleClass().add("donut-legend-name");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label amount = new Label(Formatters.money(value, "USD"));
        amount.getStyleClass().add("donut-legend-value");
        long pct = total > 0 ? Math.round(value / total * 100) : 0;
        Label percent = new Label(pct + "%");
        percent.getStyleClass().add("donut-legend-pct");
        percent.setMinWidth(52);
        percent.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(12, swatch, label, spacer, amount, percent);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /* ---- Pipeline par étape : barres horizontales colorées ---- */

    private VBox statusFunnel(List<PipelineItem> all) {
        java.util.EnumMap<DealStatus, Long> counts = new java.util.EnumMap<>(DealStatus.class);
        for (DealStatus s : DealStatus.values()) {
            counts.put(s, 0L);
        }
        for (PipelineItem i : all) {
            counts.merge(i.status(), 1L, Long::sum);
        }
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);

        VBox rows = new VBox(12);
        for (DealStatus s : DealStatus.values()) {
            rows.getChildren().add(funnelBar(s, counts.get(s), max));
        }
        return rows;
    }

    private HBox funnelBar(DealStatus status, long count, long max) {
        Label label = new Label(status.label());
        label.getStyleClass().add("funnel-label");
        label.setMinWidth(150);

        Region fill = new Region();
        fill.getStyleClass().add("funnel-fill");
        fill.getStyleClass().add(switch (status) {
            case APPROVED -> "funnel-approved";
            case DECLINED_LOST -> "funnel-declined";
            default -> "funnel-stage";
        });
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("funnel-track");
        track.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(track, Priority.ALWAYS);
        double fraction = max > 0 ? (double) count / max : 0;
        fill.maxWidthProperty().bind(track.widthProperty().multiply(fraction));

        Label value = new Label(Long.toString(count));
        value.getStyleClass().add("funnel-value");
        value.setMinWidth(36);

        HBox row = new HBox(14, label, track, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /* ---- Exposition géographique : carte du monde en heat map ---- */

    private VBox geographyChart(List<PipelineItem> active) {
        // La géographie est un pays : on compte directement par pays.
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (PipelineItem i : active) {
            String country = i.geography();
            if (country != null && !country.isBlank()) {
                counts.merge(country.trim(), 1L, Long::sum);
            }
        }
        long max = Math.max(1, counts.values().stream().mapToLong(Long::longValue).max().orElse(0L));

        WorldHeatMap map = new WorldHeatMap(counts);
        // Plafonne la taille (la carte a beaucoup d'océan) et centre-la sous les panneaux.
        map.setMaxWidth(640);
        HBox mapRow = new HBox(map);
        mapRow.setAlignment(Pos.CENTER);

        // Légende : dégradé + repères d'intensité.
        Label fewer = new Label("Fewer");
        fewer.getStyleClass().add("map-legend-cap");
        Region gradient = new Region();
        gradient.getStyleClass().add("map-legend-bar");
        gradient.setMinSize(180, 12);
        gradient.setPrefSize(180, 12);
        Label more = new Label("More");
        more.getStyleClass().add("map-legend-cap");
        Label scale = new Label("1 – " + max + " opps");
        scale.getStyleClass().add("map-legend-scale");
        HBox legend = new HBox(10, fewer, gradient, more, scale);
        legend.setAlignment(Pos.CENTER_LEFT);

        return new VBox(14, mapRow, legend);
    }

    /* ---- Opportunités : liste de décision (statut modifiable en comité) ---- */

    private VBox decisions(List<PipelineItem> all) {
        List<PipelineItem> sorted = all.stream()
                .filter(i -> i.score() != null)
                .sorted(Comparator.comparingInt(PipelineItem::score).reversed())
                .toList();

        Label title = new Label(onStatusChange != null
                ? "OPPORTUNITIES — STATUS DECISIONS" : "OPPORTUNITIES");
        title.getStyleClass().add("pres-section-title");

        // Filtre par stratégie (catégorie)
        ComboBox<String> filter = new ComboBox<>();
        filter.getItems().add(ALL_STRATEGIES);
        filter.getItems().addAll(
                Category.BUYOUT_GROWTH_VC.label(),
                Category.SECONDARIES.label(),
                Category.PRIVATE_CREDIT.label(),
                PipelineItem.DEALS_STRATEGY);
        filter.setValue(ALL_STRATEGIES);
        filter.getStyleClass().add("pres-status-combo");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(16, title, spacer, filter);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox rows = new VBox(8);
        Runnable render = () -> {
            rows.getChildren().clear();
            String f = filter.getValue();
            for (PipelineItem i : sorted) {
                if (ALL_STRATEGIES.equals(f) || i.strategy().equals(f)) {
                    rows.getChildren().add(decisionRow(i));
                }
            }
        };
        filter.valueProperty().addListener((o, a, b) -> render.run());
        render.run();

        return new VBox(14, header, rows);
    }

    private HBox decisionRow(PipelineItem i) {
        Label name = new Label(i.name());
        name.getStyleClass().add("pres-priority-name");
        name.getStyleClass().add("link-name");   // affordance visuelle (survol)
        Label metrics = new Label(headline.apply(i));
        metrics.getStyleClass().add("pres-priority-metrics");
        // Toute la zone nom + métriques est cliquable → ouvre la fiche complète (toutes les infos).
        VBox nameBox = new VBox(3, name, metrics);
        nameBox.setMinWidth(320);
        nameBox.setCursor(javafx.scene.Cursor.HAND);
        nameBox.setOnMouseClicked(e -> onOpen.accept(i));

        Label strat = new Label(i.strategy());
        strat.getStyleClass().add("pres-priority-strategy");
        strat.setMinWidth(180);

        Node statusNode;
        if (onStatusChange != null) {
            ComboBox<DealStatus> combo = FormControls.enumCombo(DealStatus.values(), DealStatus::label, false);
            combo.setValue(i.status());
            combo.getStyleClass().add("pres-status-combo");
            // Comparer à l'ancienne valeur du combo (a), pas au modèle qui n'est plus
            // rechargé : sinon revenir au statut initial ne serait pas persisté.
            combo.valueProperty().addListener((o, a, b) -> {
                if (b != null && b != a) {
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

        HBox row = new HBox(16, nameBox, strat, spacer, statusNode, score, OpportunityTable.tierNode(i.tier()));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("pres-priority-row");
        return row;
    }

}
