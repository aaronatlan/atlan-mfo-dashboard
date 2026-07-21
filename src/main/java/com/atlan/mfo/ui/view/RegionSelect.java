package com.atlan.mfo.ui.view;

import com.atlan.mfo.model.enums.Classification;
import com.atlan.mfo.model.enums.Region;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.FlowPane;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sélecteur multi-régions (cases à cocher) partagé par les formulaires fonds et deals.
 * Lecture/écriture en CSV de codes {@link Region}, cohérent avec les autres
 * multi-sélections (mandat de secondaire, sous-jacent).
 */
public final class RegionSelect extends FlowPane {

    private final Map<Region, CheckBox> boxes = new LinkedHashMap<>();

    public RegionSelect() {
        super(16, 6);
        getStyleClass().add("region-select");
        for (Region region : Region.values()) {
            CheckBox cb = new CheckBox(region.label());
            boxes.put(region, cb);
            getChildren().add(cb);
        }
    }

    /** Codes des régions cochées, joints par virgule ; {@code null} si aucune. */
    public String csv() {
        StringBuilder sb = new StringBuilder();
        for (var e : boxes.entrySet()) {
            if (e.getValue().isSelected()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(e.getKey().name());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Coche les régions présentes dans le CSV fourni (édition). */
    public void populate(String csv) {
        var on = Classification.listFromCsv(Region.class, csv);
        for (var e : boxes.entrySet()) {
            e.getValue().setSelected(on.contains(e.getKey()));
        }
    }
}
