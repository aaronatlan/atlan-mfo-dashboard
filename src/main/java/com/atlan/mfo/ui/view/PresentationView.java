package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.model.FxRates;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.Classification.AssetClass;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.model.enums.Tier;
import com.atlan.mfo.ui.util.Formatters;
import com.atlan.mfo.ui.util.FormControls;
import javafx.scene.layout.GridPane;
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

    private static final String ALL_STRATEGIES = "All asset classes";

    private final BiConsumer<PipelineItem, DealStatus> onStatusChange;
    private final Consumer<PipelineItem> onOpen;
    private final Function<PipelineItem, String> headline;
    private final List<FundInvestment> activeFunds;
    private final FxRates fx;

    /**
     * @param funds          fonds complets (avec millésimes) — nécessaires aux graphes par
     *                       classe d'actifs / par millésime (taille, cible, DPI/TVPI/IRR/MOIC)
     * @param fx             taux de change (agrégats taille/cible en USD)
     * @param onStatusChange appelé quand un statut est changé (analyste) ; {@code null}
     *                       pour un partner (statuts en lecture seule)
     * @param onOpen         ouvre la fiche complète d'une opportunité (clic sur le nom)
     * @param headline       texte résumé (millésime le plus récent / métriques) par ligne
     */
    public PresentationView(List<PipelineItem> items, List<FundInvestment> funds, FxRates fx,
                            BiConsumer<PipelineItem, DealStatus> onStatusChange,
                            Consumer<PipelineItem> onOpen, Function<PipelineItem, String> headline,
                            Runnable onExitToAnalyst, Runnable onToggleFullScreen, Runnable onLogout) {
        getStyleClass().add("presentation-root");
        this.onStatusChange = onStatusChange;
        this.onOpen = onOpen;
        this.headline = headline;
        this.fx = fx;
        this.activeFunds = funds.stream().filter(f -> f.status().isActive()).toList();

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
                twoPanels(
                        panel("AVERAGE PERFORMANCE BY ASSET CLASS — LATEST VINTAGE", performanceByClass()),
                        panel("FUNDS BY VINTAGE YEAR", fundsPerVintageChart())),
                panel("GEOGRAPHIC EXPOSURE — BY OPPORTUNITY COUNT", geographyChart(active)),
                decisions(all));
        box.getStyleClass().add("presentation-body");
        return box;
    }

    /** Deux panneaux côte à côte, largeur partagée (évite d'empiler et gagner en hauteur). */
    private HBox twoPanels(VBox left, VBox right) {
        left.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox row = new HBox(20, left, right);
        row.setFillHeight(true);
        return row;
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
        VBox sizing = panel("FUND SIZE VS TARGET RAISE — BY ASSET CLASS", fundSizeChart());
        VBox pipeline = panel("PIPELINE BY STAGE", statusFunnel(all));
        sizing.setMaxWidth(Double.MAX_VALUE);
        pipeline.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(sizing, Priority.ALWAYS);
        HBox.setHgrow(pipeline, Priority.ALWAYS);
        HBox row = new HBox(20, sizing, pipeline);
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

    /* ---- Graphes fonds : taille/cible, performance par classe, fonds par millésime ---- */

    /** Millésime le plus récent d'un fonds (porte les métriques affichées), ou null. */
    private static FundVintage latest(FundInvestment f) {
        return f.vintages() == null ? null : f.vintages().stream()
                .max(Comparator.comparingInt(FundVintage::vintageYear)).orElse(null);
    }

    private static AssetClass classOf(FundInvestment f) {
        return com.atlan.mfo.model.enums.Classification.fromCode(AssetClass.class, f.assetClass());
    }

    private double usd(Double value, String currency) {
        Double u = fx.toUsd(value, currency);
        return u == null ? 0 : u;
    }

    private Label placeholder(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("pres-priority-metrics");
        return l;
    }

    /**
     * Barre horizontale générique (réutilise le style « funnel »), pour les graphes
     * taille/cible et fonds-par-millésime.
     */
    private HBox metricBar(String label, double value, double max, String valueText, String fillClass) {
        Label l = new Label(label);
        l.getStyleClass().add("funnel-label");
        l.setMinWidth(190);
        Region fill = new Region();
        fill.getStyleClass().addAll("funnel-fill", fillClass);
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("funnel-track");
        track.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(track, Priority.ALWAYS);
        double frac = max > 0 ? value / max : 0;
        fill.maxWidthProperty().bind(track.widthProperty().multiply(frac));
        Label v = new Label(valueText);
        v.getStyleClass().add("funnel-value");
        v.setMinWidth(90);
        HBox row = new HBox(14, l, track, v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Taille de fonds levée vs cible de levée, agrégées en USD par classe d'actifs (#2). */
    private Node fundSizeChart() {
        java.util.Map<AssetClass, double[]> byClass = new java.util.LinkedHashMap<>();
        for (AssetClass ac : AssetClass.values()) {
            byClass.put(ac, new double[2]);   // [size, target]
        }
        for (FundInvestment f : activeFunds) {
            FundVintage v = latest(f);
            AssetClass ac = classOf(f);
            if (v == null || ac == null) {
                continue;
            }
            double[] agg = byClass.get(ac);
            agg[0] += usd(v.fundSize(), f.currency());
            agg[1] += usd(v.targetRaise(), f.currency());
        }
        double max = 0;
        for (double[] a : byClass.values()) {
            max = Math.max(max, Math.max(a[0], a[1]));
        }
        if (max <= 0) {
            return placeholder("No fund size or target raise reported yet.");
        }
        VBox rows = new VBox(14);
        for (var e : byClass.entrySet()) {
            double[] a = e.getValue();
            if (a[0] <= 0 && a[1] <= 0) {
                continue;
            }
            VBox group = new VBox(6,
                    metricBar(e.getKey().label() + " · size", a[0], max, Formatters.money(a[0], "USD"), "funnel-stage"),
                    metricBar(e.getKey().label() + " · target", a[1], max, Formatters.money(a[1], "USD"), "funnel-approved"));
            rows.getChildren().add(group);
        }
        return rows;
    }

    /** Moyenne des performances (DPI/TVPI/IRR/MOIC) du millésime récent, par classe (#7). */
    private Node performanceByClass() {
        java.util.Map<AssetClass, java.util.List<FundVintage>> byClass = new java.util.LinkedHashMap<>();
        for (FundInvestment f : activeFunds) {
            FundVintage v = latest(f);
            AssetClass ac = classOf(f);
            if (v == null || ac == null) {
                continue;
            }
            byClass.computeIfAbsent(ac, k -> new java.util.ArrayList<>()).add(v);
        }
        if (byClass.isEmpty()) {
            return placeholder("No fund vintage reported yet.");
        }
        GridPane g = new GridPane();
        g.getStyleClass().add("method-table");
        g.setHgap(28);
        g.setVgap(8);
        String[] heads = {"Asset class", "DPI", "TVPI", "IRR", "MOIC", "Funds"};
        for (int c = 0; c < heads.length; c++) {
            Label h = new Label(heads[c]);
            h.getStyleClass().add("method-head");
            g.add(h, c, 0);
        }
        int r = 1;
        for (var e : byClass.entrySet()) {
            java.util.List<FundVintage> vs = e.getValue();
            String[] cells = {
                    e.getKey().label(),
                    Formatters.multiple(avg(vs, FundVintage::dpi)),
                    Formatters.multiple(avg(vs, FundVintage::tvpi)),
                    Formatters.percent(avg(vs, FundVintage::irr)),
                    Formatters.multiple(avg(vs, FundVintage::moic)),
                    Integer.toString(vs.size())};
            for (int c = 0; c < cells.length; c++) {
                Label cell = new Label(cells[c]);
                cell.getStyleClass().add("method-cell");
                g.add(cell, c, r);
            }
            r++;
        }
        return g;
    }

    /** Moyenne d'une métrique de millésime sur une liste, en ignorant les valeurs absentes. */
    private static Double avg(java.util.List<FundVintage> vs, Function<FundVintage, Double> metric) {
        java.util.OptionalDouble m = vs.stream().map(metric).filter(x -> x != null)
                .mapToDouble(Double::doubleValue).average();
        return m.isPresent() ? m.getAsDouble() : null;
    }

    /** Nombre de fonds par année de millésime (#7). */
    private Node fundsPerVintageChart() {
        java.util.Map<Integer, Long> byYear = new java.util.TreeMap<>();
        for (FundInvestment f : activeFunds) {
            if (f.vintages() == null) {
                continue;
            }
            for (FundVintage v : f.vintages()) {
                byYear.merge(v.vintageYear(), 1L, Long::sum);
            }
        }
        if (byYear.isEmpty()) {
            return placeholder("No fund vintage reported yet.");
        }
        long max = byYear.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        VBox rows = new VBox(10);
        java.util.List<Integer> years = new java.util.ArrayList<>(byYear.keySet());
        java.util.Collections.reverse(years);   // plus récent en haut
        for (Integer y : years) {
            long c = byYear.get(y);
            rows.getChildren().add(metricBar(Integer.toString(y), c, max, Long.toString(c), "funnel-stage"));
        }
        return rows;
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

        // Filtre par classe d'actifs
        ComboBox<String> filter = new ComboBox<>();
        filter.getItems().add(ALL_STRATEGIES);
        for (var ac : com.atlan.mfo.model.enums.Classification.AssetClass.values()) {
            filter.getItems().add(ac.label());
        }
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
                if (ALL_STRATEGIES.equals(f) || i.assetClassLabel().equals(f)) {
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

        // Classe d'actifs + ligne secondaire « sous-stratégie · secteur » (si renseignés).
        Label strat = new Label(i.assetClassLabel());
        strat.getStyleClass().add("pres-priority-strategy");
        VBox stratBox = new VBox(3, strat);
        stratBox.setMinWidth(200);
        String detail = java.util.stream.Stream.of(i.subStrategy(), i.industry())
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("  ·  "));
        if (!detail.isEmpty()) {
            Label d = new Label(detail);
            d.getStyleClass().add("pres-priority-metrics");
            stratBox.getChildren().add(d);
        }

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

        HBox row = new HBox(16, nameBox, stratBox, spacer, statusNode, score, OpportunityTable.tierNode(i.tier()));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("pres-priority-row");
        return row;
    }

}
