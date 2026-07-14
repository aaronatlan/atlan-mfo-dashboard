package com.atlan.mfo.dao;

import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.FxRates;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accès aux taux de change éditables (table {@code fx_rate} : code de devise →
 * {@code usd_per_unit}). Les valeurs par défaut de {@link com.atlan.mfo.model.enums.Currency}
 * servent de repli si une devise n'est pas encore persistée.
 */
public final class FxRateDao {

    /** Charge les taux persistés, fusionnés avec les défauts. */
    public FxRates load() {
        Map<String, Double> overrides = new LinkedHashMap<>();
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT currency, usd_per_unit FROM fx_rate");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                overrides.put(rs.getString("currency"), rs.getDouble("usd_per_unit"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture de fx_rate impossible", e);
        }
        return FxRates.of(overrides);
    }

    /** Insère/met à jour les taux fournis (code → usdPerUnit), en transaction. */
    public void saveAll(Map<String, Double> rates) {
        String sql = """
                INSERT INTO fx_rate (currency, usd_per_unit, updated_at) VALUES (?, ?, now())
                ON CONFLICT (currency) DO UPDATE SET usd_per_unit = EXCLUDED.usd_per_unit, updated_at = now()
                """;
        try (Connection conn = Database.dataSource().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (var e : rates.entrySet()) {
                    if (e.getValue() == null || e.getValue() <= 0) {
                        continue;
                    }
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
            throw new IllegalStateException("Enregistrement de fx_rate impossible", e);
        }
    }
}
