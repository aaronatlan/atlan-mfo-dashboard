package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.model.enums.Tier;
import com.atlan.mfo.ui.util.Formatters;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
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

/** Tableau d'opportunités avec filtres (stratégie, statut), recherche et tri (voir §6.1). */
public final class OpportunityTable extends VBox {

    private static final String ALL_STRATEGIES = "Toutes les stratégies";
    private static final String ALL_STATUSES = "Tous les statuts";

    private final FilteredList<PipelineItem> filtered;
    private final ComboBox<String> strategyFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TextField search = new TextField();

    public OpportunityTable(List<PipelineItem> items, Consumer<PipelineItem> onOpen, boolean showStrategyFilter) {
        getStyleClass().add("table-block");
        setSpacing(12);

        filtered = new FilteredList<>(FXCollections.observableArrayList(items), p -> true);

        buildFilters(showStrategyFilter);
        TableView<PipelineItem> table = buildTable(onOpen);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(buildFilterRow(showStrategyFilter), table);
    }

    private HBox buildFilterRow(boolean showStrategyFilter) {
        HBox row = new HBox(10);
        row.getStyleClass().add("filter-row");
        row.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText("Rechercher…");
        search.getStyleClass().add("search-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        if (showStrategyFilter) {
            row.getChildren().add(strategyFilter);
        }
        row.getChildren().addAll(statusFilter, search);
        return row;
    }

    private void buildFilters(boolean showStrategyFilter) {
        strategyFilter.getItems().add(ALL_STRATEGIES);
        strategyFilter.getItems().addAll(
                com.atlan.mfo.model.enums.Category.BUYOUT_GROWTH_VC.label(),
                com.atlan.mfo.model.enums.Category.SECONDARIES.label(),
                com.atlan.mfo.model.enums.Category.PRIVATE_CREDIT.label(),
                PipelineItem.DEALS_STRATEGY);
        strategyFilter.setValue(ALL_STRATEGIES);

        statusFilter.getItems().add(ALL_STATUSES);
        for (DealStatus s : DealStatus.values()) {
            statusFilter.getItems().add(s.label());
        }
        statusFilter.setValue(ALL_STATUSES);

        strategyFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        statusFilter.valueProperty().addListener((o, a, b) -> applyPredicate());
        search.textProperty().addListener((o, a, b) -> applyPredicate());
    }

    private void applyPredicate() {
        String strat = strategyFilter.getValue();
        String status = statusFilter.getValue();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();

        filtered.setPredicate(item -> {
            if (strat != null && !ALL_STRATEGIES.equals(strat) && !item.strategy().equals(strat)) {
                return false;
            }
            if (status != null && !ALL_STATUSES.equals(status) && !item.status().label().equals(status)) {
                return false;
            }
            if (!q.isEmpty() && !item.name().toLowerCase().contains(q)) {
                return false;
            }
            return true;
        });
    }

    private TableView<PipelineItem> buildTable(Consumer<PipelineItem> onOpen) {
        TableView<PipelineItem> table = new TableView<>();
        table.getStyleClass().add("opportunity-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Aucune opportunité"));

        TableColumn<PipelineItem, String> nameCol = new TableColumn<>("Nom");
        nameCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name()));
        nameCol.setMaxWidth(3000);

        TableColumn<PipelineItem, String> stratCol = new TableColumn<>("Stratégie");
        stratCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().strategy()));

        TableColumn<PipelineItem, String> statusCol = new TableColumn<>("Statut");
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status().label()));

        TableColumn<PipelineItem, Integer> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().score()));
        scoreCol.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        scoreCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : Formatters.score(value));
            }
        });
        scoreCol.setStyle("-fx-alignment: CENTER-RIGHT;");

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
        box.getChildren().addAll(dot, label);
        return box;
    }
}
