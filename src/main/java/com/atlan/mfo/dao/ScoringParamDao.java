package com.atlan.mfo.dao;

import com.atlan.mfo.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Accès aux paramètres de scoring modifiables (table {@code scoring_param}). */
public final class ScoringParamDao {

    /** Tous les paramètres persistés (nom → valeur). */
    public Map<String, Double> loadAll() {
        Map<String, Double> params = new LinkedHashMap<>();
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name, value FROM scoring_param");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                params.put(rs.getString("name"), rs.getDouble("value"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture de scoring_param impossible", e);
        }
        return params;
    }

    /** Insère/met à jour les paramètres fournis (transaction). */
    public void saveAll(Map<String, Double> params) {
        String sql = """
                INSERT INTO scoring_param (name, value) VALUES (?, ?)
                ON CONFLICT (name) DO UPDATE SET value = EXCLUDED.value
                """;
        try (Connection conn = Database.dataSource().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (var e : params.entrySet()) {
                    ps.setString(1, e.getKey());
                    ps.setDouble(2, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Enregistrement de scoring_param impossible", e);
        }
    }
}
