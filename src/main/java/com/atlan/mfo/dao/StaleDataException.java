package com.atlan.mfo.dao;

/**
 * Levée lorsqu'un UPDATE sous verrou optimiste n'affecte aucune ligne : la fiche
 * a été modifiée par un autre utilisateur depuis son chargement (voir §13.2).
 */
public class StaleDataException extends RuntimeException {

    public StaleDataException(String message) {
        super(message);
    }
}
