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
        cb.getItems().addAll("US", "EUROPE", "UK", "GLOBAL", "OTHER");
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

    /** ComboBox des devises supportées (codes ISO) ; défaut USD (devise de référence). */
    public static ComboBox<String> currencyCombo() {
        ComboBox<String> cb = new ComboBox<>();
        for (com.atlan.mfo.model.enums.Currency c : com.atlan.mfo.model.enums.Currency.values()) {
            cb.getItems().add(c.code());
        }
        cb.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                if (code == null) {
                    return "";
                }
                return code + " — " + com.atlan.mfo.model.enums.Currency.fromCode(code).label();
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        cb.setValue(com.atlan.mfo.model.enums.Currency.REFERENCE.code());
        return cb;
    }

    public static TextField field(String prompt) {
        TextField t = new TextField();
        t.setPromptText(prompt);
        return t;
    }

    /**
     * ComboBox de chaînes <b>éditable</b> : choix dans la liste proposée, ou saisie
     * libre. La valeur retenue est le texte de l'éditeur (voir {@link #comboText}).
     */
    public static ComboBox<String> editableCombo(String prompt, String... options) {
        ComboBox<String> cb = new ComboBox<>();
        cb.setEditable(true);
        cb.getItems().addAll(options);
        cb.getEditor().setPromptText(prompt);
        cb.setMaxWidth(Double.MAX_VALUE);
        return cb;
    }

    /** Texte courant d'un combo éditable (éditeur), ou {@code null} si vide. */
    public static String comboText(ComboBox<String> cb) {
        String s = cb.getEditor().getText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
