package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.Tier;
import com.atlan.mfo.ui.util.Formatters;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Bande de KPI du Pipeline summary (voir §6.1). Calcul sur les opportunités actives. */
public final class KpiBar extends HBox {

    public KpiBar(List<PipelineItem> items) {
        getStyleClass().add("kpi-bar");
        setSpacing(16);

        List<PipelineItem> active = items.stream().filter(PipelineItem::isActive).toList();

        long activeCount = active.size();
        double capital = active.stream()
                .map(PipelineItem::commitment)
                .filter(c -> c != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        var scores = active.stream()
                .map(PipelineItem::score)
                .filter(s -> s != null)
                .mapToInt(Integer::intValue);
        var avg = scores.average();
        long strongCount = active.stream()
                .filter(i -> i.tier() == Tier.STRONG)
                .count();

        getChildren().addAll(
                card(Long.toString(activeCount), "ACTIVE DEALS"),
                card(Formatters.money(capital), "CAPITAL UNDER REVIEW"),
                card(avg.isPresent() ? Long.toString(Math.round(avg.getAsDouble())) : "—", "AVERAGE SCORE"),
                card(Long.toString(strongCount), "STRONG TIER"));
    }

    private VBox card(String value, String label) {
        Label v = new Label(value);
        v.getStyleClass().add("kpi-value");
        Label l = new Label(label);
        l.getStyleClass().add("kpi-label");
        VBox box = new VBox(4, v, l);
        box.getStyleClass().add("kpi-card");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}
