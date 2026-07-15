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

/**
 * Carte du monde (choroplèthe) colorée en heat map selon le nombre d'opportunités
 * <b>par pays</b>. La topologie (Natural Earth, domaine public) est embarquée en
 * ressource — un tracé SVG par pays — donc aucun accès réseau : rendu 100 % hors-ligne
 * (§6.3, confidentialité). La géographie d'une opportunité étant un pays, chaque pays
 * est colorié directement d'après son nombre d'opportunités.
 */
public final class WorldHeatMap extends Pane {

    private static final double VW = 900, VH = 470;
    private static final Color NO_DATA = Color.web("#22495A");
    private static final Color OCEAN_STROKE = Color.web("#FFFFFF", 0.10);
    private static final Color HOVER_STROKE = Color.web("#FFFFFF", 0.9);

    // Rampe « chaleur » alignée sur le thème : pétrole → bronze → terracotta.
    private static final Color[] RAMP = {
            Color.web("#2E5A62"), Color.web("#6E6A44"), Color.web("#9A6E3A"), Color.web("#C0796A")};

    private final Group content = new Group();

    /**
     * @param countryCounts nombre d'opportunités par pays (clé = nom du pays tel que
     *                      dans l'asset carte, ex. « United States of America »)
     */
    public WorldHeatMap(Map<String, Long> countryCounts) {
        getStyleClass().add("world-heatmap");
        long max = 1;
        for (long v : countryCounts.values()) {
            max = Math.max(max, v);
        }
        Map<String, String> paths = load();
        for (Map.Entry<String, String> e : paths.entrySet()) {
            String country = e.getKey();
            SVGPath p = new SVGPath();
            p.setContent(e.getValue());
            long count = countryCounts.getOrDefault(country, 0L);
            final Color base = count > 0 ? heat(count, max) : NO_DATA;
            p.setFill(base);
            p.setStroke(OCEAN_STROKE);
            p.setStrokeWidth(0.5);

            // Tooltip : pays + nombre d'opportunités s'il y en a, sinon nom seul.
            String tipText = count > 0
                    ? country + " — " + count + (count == 1 ? " opportunity" : " opportunities")
                    : country;
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

    /**
     * Couleur de chaleur pour {@code count} dans (0, max] : la <b>teinte</b> (pétrole →
     * bronze → terracotta) <b>et l'intensité</b> (opacité) croissent avec le nombre. Un
     * plancher garantit qu'un seul opportunité reste bien visible (pas confondu avec le
     * fond « sans donnée »).
     */
    private static Color heat(long count, long max) {
        double frac = max > 1 ? (count - 1.0) / (max - 1.0) : 1.0;
        frac = Math.max(0, Math.min(1, frac));
        double t = 0.35 + 0.65 * frac;          // plancher : 1 opp = déjà bronze visible
        double alpha = 0.55 + 0.45 * frac;      // intensité : plus d'opps = plus opaque
        return rampAt(t).deriveColor(0, 1, 1, alpha);
    }

    /** Interpolation d'une position {@code t} in [0,1] le long de la rampe de teinte. */
    private static Color rampAt(double t) {
        double scaled = Math.max(0, Math.min(1, t)) * (RAMP.length - 1);
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
