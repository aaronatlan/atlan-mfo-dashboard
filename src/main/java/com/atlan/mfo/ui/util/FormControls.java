package com.atlan.mfo.ui.util;

import com.atlan.mfo.scoring.MetricParser;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.OptionalDouble;
import java.util.function.Function;

/** Fabriques de contrôles pour les formulaires de saisie (Phase 3). */
public final class FormControls {

    private FormControls() {
    }

    /** Parse une saisie brute en valeur décimale (via {@link MetricParser}), ou {@code null}. */
    public static Double parse(String raw) {
        OptionalDouble o = MetricParser.parse(raw);
        return o.isPresent() ? o.getAsDouble() : null;
    }

    /** ComboBox d'enum avec libellés ; {@code allowNull} ajoute une entrée « — » = non renseigné. */
    public static <E> ComboBox<E> enumCombo(E[] values, Function<E, String> labeler, boolean allowNull) {
        ComboBox<E> cb = new ComboBox<>();
        if (allowNull) {
            cb.getItems().add(null);
        }
        cb.getItems().addAll(values);
        cb.setConverter(new StringConverter<>() {
            @Override
            public String toString(E e) {
                return e == null ? "—" : labeler.apply(e);
            }

            @Override
            public E fromString(String s) {
                return null;
            }
        });
        return cb;
    }

    /** ComboBox des tokens géographiques canoniques (§13.1) ; « — » = non renseigné. */
    public static ComboBox<String> geographyCombo() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().add(null);
        cb.getItems().addAll("US", "EUROPE", "UK", "DACH", "GLOBAL", "OTHER");
        cb.setConverter(new StringConverter<>() {
            @Override
            public String toString(String s) {
                return s == null ? "—" : s;
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        return cb;
    }

    public static TextField field(String prompt) {
        TextField t = new TextField();
        t.setPromptText(prompt);
        return t;
    }
}
