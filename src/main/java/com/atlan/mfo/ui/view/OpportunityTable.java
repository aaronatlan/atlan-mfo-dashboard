package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.Classification;
import com.atlan.mfo.model.enums.Classification.AccessRoute;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.model.enums.Tier;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tableau d'opportunités : filtres par route d'accès, sous-stratégie, statut, tier,
 * secteur, actifs, plus recherche et tri (§6.1, structure Patrimium). Les filtres de
 * classification n'apparaissent que si des valeurs sont présentes dans la liste.
 */
public final class OpportunityTable extends VBox {

    private static final String ALL_ROUTES = "All access routes";
    private static final String ALL_SUBSTRATS = "All sub-strategies";
    private static final String ALL_STATUSES = "All statuses";
    private static final String ALL_TIERS = "All tiers";
    private static final String ALL_INDUSTRIES = "All industries";

    private final int total;
    private final boolean hasRoute;
    private final boolean hasSubStrat;
    private final boolean hasIndustry;
    private final FilteredList<PipelineItem> filtered;

    private final ComboBox<String> routeFilter = new ComboBox<>();
    private final ComboBox<String> subStratFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> tierFilter = new ComboBox<>();
    private final ComboBox<String> industryFilter = new ComboBox<>();
    private final CheckBox activeOnly = new CheckBox("Active only");
    private final TextField search = new TextField();
    private final Label countLabel = new Label();
    private final Label reset = new Label("Reset");

    public OpportunityTable(List<PipelineItem> items, Consumer<PipelineItem> onOpen) {
        getStyleClass().add("table-block");
        setSpacing(12);
        this.total = items.size();

        List<String> routes = items.stream()
                .map(i -> routeLabel(i.accessRoute()))
                .filter(s -> s != null).distinct().sorted().toList();
        this.hasRoute = !routes.isEmpty();

        List<String> subStrats = items.stream()
                .map(PipelineItem::subStrategy)
                .filter(s -> s != null && !s.isBlank())
                .distinct().sorted().toList();
        this.hasSubStrat = !subStrats.isEmpty();

        List<String> industries = items.stream()
                .map(PipelineItem::industry)
                .filter(s -> s != null && !s.isBlank())
                .distinct().sorted().toList();
        this.hasIndustry = !industries.isEmpty();

        filtered = new FilteredList<>(FXCollections.observableArrayList(items), p -> true);

        buildFilters(routes, subStrats, industries);
        TableView<PipelineItem> table = buildTable(onOpen);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(buildFilterRow(), table);
        updateCount();
    }

    static String routeLabel(String code) {
        return Classification.label(AccessRoute.class, code, AccessRoute::label);
    }

    private HBox buildFilterRow() {
        HBox row = new HBox(10);
        row.getStyleClass().add("filter-row");
        row.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText("Search…");
        search.getStyleClass().add("search-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        countLabel.getStyleClass().add("filter-count");

        reset.getStyleClass().add("link-label");
        reset.setOnMouseClicked(e -> resetFilters());
        reset.setVisible(false);
        reset.setManaged(false);

        if (hasRoute) {
            row.getChildren().add(routeFilter);
        }
        if (hasSubStrat) {
            row.getChildren().add(subStratFilter);
        }
        row.getChildren().addAll(statusFilter, tierFilter);
        if (hasIndustry) {
            row.getChildren().add(industryFilter);
        }
        row.getChildren().addAll(activeOnly, search, countLabel, reset);
        return row;
    }

    private void buildFilters(List<String> routes, List<String> subStrats, List<String> industries) {
        if (hasRoute) {
            routeFilter.getItems().add(ALL_ROUTES);
            routeFilter.getItems().addAll(routes);
            routeFilter.setValue(ALL_ROUTES);
            routeFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        }
        if (hasSubStrat) {
            subStratFilter.getItems().add(ALL_SUBSTRATS);
            subStratFilter.getItems().addAll(subStrats);
            subStratFilter.setValue(ALL_SUBSTRATS);
            subStratFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        }

        statusFilter.getItems().add(ALL_STATUSES);
        for (DealStatus s : DealStatus.values()) {
            statusFilter.getItems().add(s.label());
        }
        statusFilter.setValue(ALL_STATUSES);

        tierFilter.getItems().add(ALL_TIERS);
        for (Tier t : Tier.values()) {
            tierFilter.getItems().add(t.label());
        }
        tierFilter.setValue(ALL_TIERS);

        if (hasIndustry) {
            industryFilter.getItems().add(ALL_INDUSTRIES);
            industryFilter.getItems().addAll(industries);
            industryFilter.setValue(ALL_INDUSTRIES);
            industryFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        }

        statusFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        tierFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        activeOnly.selectedProperty().addListener((o, a, b) -> applyPredicate());
        search.textProperty().addListener((o, a, b) -> applyPredicate());
    }

    private void applyPredicate() {
        String route = hasRoute ? routeFilter.getValue() : null;
        String sub = hasSubStrat ? subStratFilter.getValue() : null;
        String status = statusFilter.getValue();
        String tier = tierFilter.getValue();
        String industry = hasIndustry ? industryFilter.getValue() : null;
        boolean active = activeOnly.isSelected();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();

        filtered.setPredicate(item -> {
            if (route != null && !ALL_ROUTES.equals(route) && !route.equals(routeLabel(item.accessRoute()))) {
                return false;
            }
            if (sub != null && !ALL_SUBSTRATS.equals(sub) && !sub.equals(item.subStrategy())) {
                return false;
            }
            if (status != null && !ALL_STATUSES.equals(status) && !item.status().label().equals(status)) {
                return false;
            }
            if (tier != null && !ALL_TIERS.equals(tier)
                    && (item.tier() == null || !item.tier().label().equals(tier))) {
                return false;
            }
            if (industry != null && !ALL_INDUSTRIES.equals(industry) && !industry.equals(item.industry())) {
                return false;
            }
            if (active && !item.isActive()) {
                return false;
            }
            return q.isEmpty() || item.name().toLowerCase().contains(q);
        });
        updateCount();

        boolean anyActive = (route != null && !ALL_ROUTES.equals(route))
                || (sub != null && !ALL_SUBSTRATS.equals(sub))
                || !ALL_STATUSES.equals(status) || !ALL_TIERS.equals(tier)
                || (industry != null && !ALL_INDUSTRIES.equals(industry))
                || active || !q.isEmpty();
        reset.setVisible(anyActive);
        reset.setManaged(anyActive);
    }

    private void updateCount() {
        int shown = filtered.size();
        countLabel.setText(shown == total ? total + " opportunities" : shown + " / " + total);
    }

    private void resetFilters() {
        if (hasRoute) {
            routeFilter.setValue(ALL_ROUTES);
        }
        if (hasSubStrat) {
            subStratFilter.setValue(ALL_SUBSTRATS);
        }
        statusFilter.setValue(ALL_STATUSES);
        tierFilter.setValue(ALL_TIERS);
        if (hasIndustry) {
            industryFilter.setValue(ALL_INDUSTRIES);
        }
        activeOnly.setSelected(false);
        search.clear();
    }

    private TableView<PipelineItem> buildTable(Consumer<PipelineItem> onOpen) {
        TableView<PipelineItem> table = new TableView<>();
        table.getStyleClass().add("opportunity-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No opportunity matches the filters"));

        TableColumn<PipelineItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name()));
        nameCol.setMaxWidth(3000);
        nameCol.getStyleClass().add("col-primary");

        TableColumn<PipelineItem, String> subCol = new TableColumn<>("Sub-strategy");
        subCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().subStrategy() == null ? "" : c.getValue().subStrategy()));
        subCol.getStyleClass().add("col-secondary");

        TableColumn<PipelineItem, String> industryCol = new TableColumn<>("Industry");
        industryCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().industry() == null ? "" : c.getValue().industry()));
        industryCol.getStyleClass().add("col-secondary");

        TableColumn<PipelineItem, PipelineItem> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        statusCol.setComparator(Comparator.comparing(i -> i.status().label()));
        statusCol.getStyleClass().add("col-secondary");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(PipelineItem item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : statusNode(item));
            }
        });

        TableColumn<PipelineItem, Integer> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().score()));
        scoreCol.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        scoreCol.getStyleClass().add("col-score");

        TableColumn<PipelineItem, PipelineItem> dataCol = new TableColumn<>("Data");
        dataCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        dataCol.setComparator(Comparator.comparingDouble(
                (PipelineItem i) -> i.criteria() == 0 ? 0 : (double) i.reported() / i.criteria()));
        dataCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(PipelineItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label l = new Label(item.completeness());
                l.getStyleClass().add("completeness");
                if (item.reported() < item.criteria()) {
                    l.getStyleClass().add("completeness-low");
                }
                HBox box = new HBox(l);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        TableColumn<PipelineItem, PipelineItem> tierCol = new TableColumn<>("Tier");
        tierCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        tierCol.setComparator(Comparator.comparingInt(
                (PipelineItem i) -> i.tier() == null ? Integer.MAX_VALUE : i.tier().ordinal()));
        tierCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(PipelineItem item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : tierNode(item.tier()));
            }
        });

        table.getColumns().add(nameCol);
        table.getColumns().add(subCol);
        if (hasIndustry) {
            table.getColumns().add(industryCol);
        }
        table.getColumns().add(statusCol);
        table.getColumns().add(scoreCol);
        table.getColumns().add(dataCol);
        table.getColumns().add(tierCol);

        SortedList<PipelineItem> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        scoreCol.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(scoreCol);

        table.setRowFactory(tv -> {
            var r = new javafx.scene.control.TableRow<PipelineItem>();
            r.itemProperty().addListener((o, a, b) -> {
                r.getStyleClass().removeAll("row-approved", "row-declined");
                if (b != null) {
                    if (b.status() == DealStatus.APPROVED) {
                        r.getStyleClass().add("row-approved");
                    } else if (b.status() == DealStatus.DECLINED_LOST) {
                        r.getStyleClass().add("row-declined");
                    }
                }
            });
            r.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !r.isEmpty()) {
                    onOpen.accept(r.getItem());
                }
            });
            return r;
        });

        return table;
    }

    /** Libellé de statut + puce « Decided » pour les opportunités décidées (§6.1). */
    static HBox statusNode(PipelineItem item) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        Label status = new Label(item.status().label());
        status.getStyleClass().add("cell-status");
        box.getChildren().add(status);
        if (item.isDecided()) {
            Label chip = new Label("Decided");
            chip.getStyleClass().add("decided-chip");
            box.getChildren().add(chip);
        }
        return box;
    }

    /** Indicateur de tier : point coloré + libellé (registre sobre, voir §8.1). */
    static HBox tierNode(Tier tier) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        if (tier == null) {
            box.getChildren().add(new Label("—"));
            return box;
        }
        Region dot = new Region();
        dot.getStyleClass().addAll("tier-dot", "tier-" + tier.name().toLowerCase());
        Label label = new Label(tier.label());
        label.getStyleClass().add("tier-name");
        box.getChildren().addAll(dot, label);
        return box;
    }
}
