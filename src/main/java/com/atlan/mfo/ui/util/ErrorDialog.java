package com.atlan.mfo.ui.util;

import javafx.scene.control.Alert;

/**
 * Dialogue d'erreur uniforme, affiché lorsqu'une action base échoue (base
 * injoignable, coupure réseau, requête rejetée). Évite l'échec silencieux : sans
 * lui, l'exception remonterait sur le thread UI et l'action semblerait ne rien faire.
 */
public final class ErrorDialog {

    private static final String CSS = "/css/atlan-dark.css";

    private ErrorDialog() {
    }

    /** Affiche l'erreur (message racine en détail) sur le thread UI. */
    public static void show(Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("The action could not be completed");
        alert.setContentText(
                "The database could not be reached, or the operation was rejected.\n"
                        + "Please check your connection and try again.\n\n"
                        + "Details: " + rootMessage(error));
        alert.setGraphic(null);                       // retire l'icône système
        alert.getDialogPane().setGraphic(null);
        var css = ErrorDialog.class.getResource(CSS);
        if (css != null) {
            alert.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        alert.showAndWait();
    }

    /** Message de la cause la plus profonde (la plus parlante côté diagnostic). */
    private static String rootMessage(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
    }
}
