package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.Category;
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

/** Tableau d'opportunités : filtres (stratégie, statut, tier, actifs), recherche et tri (voir §6.1). */
public final class OpportunityTable extends VBox {

    private static final String ALL_STRATEGIES = "Toutes les stratégies";
    private static final String ALL_STATUSES = "Tous les statuts";
    private static final String ALL_TIERS = "Tous les tiers";

    private final int total;
    private final boolean showStrategyFilter;
    private final FilteredList<PipelineItem> filtered;

    private final ComboBox<String> strategyFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> tierFilter = new ComboBox<>();
    private final CheckBox activeOnly = new CheckBox("Actifs uniquement");
    private final TextField search = new TextField();
    private final Label countLabel = new Label();

    public OpportunityTable(List<PipelineItem> items, Consumer<PipelineItem> onOpen, boolean showStrategyFilter) {
        getStyleClass().add("table-block");
        setSpacing(12);
        this.total = items.size();
        this.showStrategyFilter = showStrategyFilter;

        filtered = new FilteredList<>(FXCollections.observableArrayList(items), p -> true);

        buildFilters();
        TableView<PipelineItem> table = buildTable(onOpen);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(buildFilterRow(), table);
        updateCount();
    }

    private HBox buildFilterRow() {
        HBox row = new HBox(10);
        row.getStyleClass().add("filter-row");
        row.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText("Rechercher…");
        search.getStyleClass().add("search-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        countLabel.getStyleClass().add("filter-count");

        Label reset = new Label("Réinitialiser");
        reset.getStyleClass().add("link-label");
        reset.setOnMouseClicked(e -> resetFilters());

        if (showStrategyFilter) {
            row.getChildren().add(strategyFilter);
        }
        row.getChildren().addAll(statusFilter, tierFilter, activeOnly, search, countLabel, reset);
        return row;
    }

    private void buildFilters() {
        strategyFilter.getItems().add(ALL_STRATEGIES);
        strategyFilter.getItems().addAll(
                Category.BUYOUT_GROWTH_VC.label(),
                Category.SECONDARIES.label(),
                Category.PRIVATE_CREDIT.label(),
                PipelineItem.DEALS_STRATEGY);
        strategyFilter.setValue(ALL_STRATEGIES);

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

        strategyFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        statusFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        tierFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        activeOnly.selectedProperty().addListener((o, a, b) -> applyPredicate());
        search.textProperty().addListener((o, a, b) -> applyPredicate());
    }

    private void applyPredicate() {
        String strat = strategyFilter.getValue();
        String status = statusFilter.getValue();
        String tier = tierFilter.getValue();
        boolean active = activeOnly.isSelected();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();

        filtered.setPredicate(item -> {
            if (strat != null && !ALL_STRATEGIES.equals(strat) && !item.strategy().equals(strat)) {
                return false;
            }
            if (status != null && !ALL_STATUSES.equals(status) && !item.status().label().equals(status)) {
                return false;
            }
            if (tier != null && !ALL_TIERS.equals(tier)
                    && (item.tier() == null || !item.tier().label().equals(tier))) {
                return false;
            }
            if (active && !item.isActive()) {
                return false;
            }
            return q.isEmpty() || item.name().toLowerCase().contains(q);
        });
        updateCount();
    }

    private void updateCount() {
        int shown = filtered.size();
        countLabel.setText(shown == total ? total + " opportunités" : shown + " / " + total);
    }

    private void resetFilters() {
        strategyFilter.setValue(ALL_STRATEGIES);
        statusFilter.setValue(ALL_STATUSES);
        tierFilter.setValue(ALL_TIERS);
        activeOnly.setSelected(false);
        search.clear();
    }

    private TableView<PipelineItem> buildTable(Consumer<PipelineItem> onOpen) {
        TableView<PipelineItem> table = new TableView<>();
        table.getStyleClass().add("opportunity-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucune opportunité ne correspond aux filtres"));

        TableColumn<PipelineItem, String> nameCol = new TableColumn<>("Nom");
        nameCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name()));
        nameCol.setMaxWidth(3000);
        nameCol.getStyleClass().add("col-primary");     // nom en avant (blanc, semi-gras)

        TableColumn<PipelineItem, String> stratCol = new TableColumn<>("Stratégie");
        stratCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().strategy()));
        stratCol.getStyleClass().add("col-secondary");  // texte en retrait

        TableColumn<PipelineItem, String> statusCol = new TableColumn<>("Statut");
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status().label()));
        statusCol.getStyleClass().add("col-secondary");

        // Cellule par défaut : une cellule custom + classe CSS de colonne décale le
        // texte verticalement (travers du skin JavaFX vérifié par mesure). Le score
        // n'est jamais null en pratique (recalcul systématique par le moteur, §13.4).
        TableColumn<PipelineItem, Integer> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().score()));
        scoreCol.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        scoreCol.getStyleClass().add("col-score");   // centré + gras (CSS)

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
        table.getColumns().add(stratCol);
        table.getColumns().add(statusCol);
        table.getColumns().add(scoreCol);
        table.getColumns().add(tierCol);

        SortedList<PipelineItem> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        // Tri par défaut : score décroissant (§6.1)
        scoreCol.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(scoreCol);

        table.setRowFactory(tv -> {
            var r = new javafx.scene.control.TableRow<PipelineItem>();
            r.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !r.isEmpty()) {
                    onOpen.accept(r.getItem());
                }
            });
            return r;
        });

        return table;
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
        label.getStyleClass().add("tier-name");   // sinon le texte reste sombre dans les cellules
        box.getChildren().addAll(dot, label);
        return box;
    }
}
