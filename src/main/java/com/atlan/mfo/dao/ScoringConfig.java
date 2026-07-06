package com.atlan.mfo.dao;

import com.atlan.mfo.scoring.ScoringEngine;
import com.atlan.mfo.scoring.ScoringProfile;

/**
 * Fait le pont entre les paramètres persistés (table {@code scoring_param}) et le
 * moteur pur : construit le {@link ScoringProfile} courant, en repliant sur les
 * valeurs par défaut si un paramètre — ou la table — est absent.
 */
public final class ScoringConfig {

    private final ScoringParamDao dao = new ScoringParamDao();

    /**
     * Profil courant. En cas d'échec (table absente en production non migrée, par
     * exemple), retourne les valeurs par défaut sans faire planter l'application.
     */
    public ScoringProfile currentProfile() {
        try {
            return ScoringProfile.fromMap(dao.loadAll());
        } catch (RuntimeException e) {
            return ScoringProfile.defaults();
        }
    }

    public ScoringEngine currentEngine() {
        return new ScoringEngine(currentProfile());
    }

    /** Persiste un nouveau jeu de paramètres. */
    public void save(java.util.Map<String, Double> params) {
        dao.saveAll(params);
    }
}
