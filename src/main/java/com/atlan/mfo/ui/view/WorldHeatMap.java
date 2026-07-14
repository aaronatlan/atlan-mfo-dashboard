package com.atlan.mfo.ui.view;

import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Carte du monde (choroplèthe) colorée en heat map selon le nombre d'opportunités
 * par région. La topologie (Natural Earth, domaine public) est embarquée en ressource
 * — un tracé SVG par pays — donc aucun accès réseau : rendu 100 % hors-ligne (§6.3,
 * confidentialité). Les tokens géographiques régionaux (US, EUROPE, UK) sont projetés
 * sur les pays correspondants ; GLOBAL/OTHER ne pointent aucun pays et sont traités en
 * légende par la vue appelante.
 */
public final class WorldHeatMap extends Pane {

    private static final double VW = 900, VH = 470;
    private static final Color NO_DATA = Color.web("#22495A");
    private static final Color OCEAN_STROKE = Color.web("#FFFFFF", 0.10);
    private static final Color HOVER_STROKE = Color.web("#FFFFFF", 0.9);

    // Rampe « chaleur » alignée sur le thème : pétrole → bronze → terracotta.
    private static final Color[] RAMP = {
            Color.web("#2E5A62"), Color.web("#6E6A44"), Color.web("#9A6E3A"), Color.web("#C0796A")};

    private static final Set<String> US = Set.of("United States of America");
    private static final Set<String> UK = Set.of("United Kingdom");
    // EUROPE inclut désormais l'Allemagne / l'Autriche / la Suisse (DACH supprimé).
    private static final Set<String> EUROPE = Set.of(
            "Germany", "Austria", "Switzerland", "France", "Italy", "Spain", "Netherlands", "Belgium",
            "Sweden", "Norway", "Denmark", "Finland", "Poland", "Ireland", "Portugal", "Czechia",
            "Greece", "Hungary", "Romania", "Bulgaria", "Croatia", "Slovakia", "Slovenia", "Lithuania",
            "Latvia", "Estonia", "Luxembourg", "Iceland");

    private final Group content = new Group();

    /**
     * @param regionCounts nombre d'opportunités par token régional cartographiable
     *                     (clés attendues : {@code US}, {@code EUROPE}, {@code UK})
     */
    public WorldHeatMap(Map<String, Long> regionCounts) {
        getStyleClass().add("world-heatmap");
        long max = 1;
        for (long v : regionCounts.values()) {
            max = Math.max(max, v);
        }
        Map<String, String> paths = load();
        for (Map.Entry<String, String> e : paths.entrySet()) {
            String country = e.getKey();
            SVGPath p = new SVGPath();
            p.setContent(e.getValue());
            long count = countFor(country, regionCounts);
            final Color base = count > 0 ? heat(count, max) : NO_DATA;
            p.setFill(base);
            p.setStroke(OCEAN_STROKE);
            p.setStrokeWidth(0.5);

            // Tooltip : région + nombre d'opportunités si le pays est couvert, sinon nom seul.
            String region = regionOf(country);
            String tipText = region == null
                    ? country
                    : country + " — " + regionLabel(region) + ": " + count
                            + (count == 1 ? " opportunity" : " opportunities");
            Tooltip tip = new Tooltip(tipText);
            tip.setShowDelay(Duration.millis(120));
            Tooltip.install(p, tip);

            // Survol : contour clair + léger éclaircissement + passage au premier plan.
            p.setCursor(javafx.scene.Cursor.HAND);
            p.setOnMouseEntered(ev -> {
                p.setStroke(HOVER_STROKE);
                p.setStrokeWidth(1.3);
                p.setFill(base.interpolate(Color.WHITE, 0.22));
                p.toFront();
            });
            p.setOnMouseExited(ev -> {
                p.setStroke(OCEAN_STROKE);
                p.setStrokeWidth(0.5);
                p.setFill(base);
            });
            content.getChildren().add(p);
        }
        getChildren().add(content);
        // La hauteur suit la largeur (ratio de la carte) : sans ça le parent alloue une
        // hauteur fixe et la carte, mise à l'échelle sur la largeur, déborde sur les voisins.
        prefHeightProperty().bind(widthProperty().multiply(VH / VW));
    }

    private static long countFor(String country, Map<String, Long> counts) {
        if (US.contains(country)) {
            return counts.getOrDefault("US", 0L);
        }
        if (UK.contains(country)) {
            return counts.getOrDefault("UK", 0L);
        }
        if (EUROPE.contains(country)) {
            return counts.getOrDefault("EUROPE", 0L);
        }
        return 0;
    }

    /** Token régional d'un pays cartographié, ou {@code null} si non couvert. */
    private static String regionOf(String country) {
        if (US.contains(country)) {
            return "US";
        }
        if (UK.contains(country)) {
            return "UK";
        }
        if (EUROPE.contains(country)) {
            return "EUROPE";
        }
        return null;
    }

    private static String regionLabel(String region) {
        return switch (region) {
            case "US" -> "US";
            case "UK" -> "UK";
            case "EUROPE" -> "Europe";
            default -> region;
        };
    }

    /** Interpolation sur la rampe pour {@code count} dans (0, max]. */
    private static Color heat(long count, long max) {
        double t = max > 1 ? (count - 1.0) / (max - 1.0) : 1.0;
        t = Math.max(0, Math.min(1, t));
        double scaled = t * (RAMP.length - 1);
        int i = (int) Math.floor(scaled);
        if (i >= RAMP.length - 1) {
            return RAMP[RAMP.length - 1];
        }
        return RAMP[i].interpolate(RAMP[i + 1], scaled - i);
    }

    private static Map<String, String> load() {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream in = WorldHeatMap.class.getResourceAsStream("/map/world-natural-earth.tsv")) {
            if (in == null) {
                return map;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    map.put(line.substring(0, tab), line.substring(tab + 1));
                }
            }
        } catch (Exception ignored) {
            // Carte absente/corrompue : le panneau restera vide plutôt que de planter la présentation.
        }
        return map;
    }

    @Override
    protected double computePrefWidth(double height) {
        return VW;
    }

    @Override
    protected void layoutChildren() {
        double s = getWidth() / VW;
        content.setScaleX(s);
        content.setScaleY(s);
        // La mise à l'échelle pivote autour du centre du groupe : on recentre ensuite.
        content.setLayoutX((getWidth() - VW) / 2);
        content.setLayoutY((getHeight() - VH) / 2);
    }
}
