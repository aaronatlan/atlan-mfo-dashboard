package com.atlan.mfo.dao;

import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.Outcome;
import com.atlan.mfo.model.enums.OutcomeState;
import com.atlan.mfo.util.JdbcSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès aux résultats réalisés (table {@code opportunity_outcome}, boucle de
 * calibration prédit → réalisé).
 *
 * <p>Dégradation gracieuse : si la table n'existe pas encore (base de production
 * non migrée), la lecture renvoie une liste vide plutôt que de faire planter
 * l'application ; l'écriture lève un message explicite invitant à migrer.
 */
public final class OutcomeDao {

    /** SQLSTATE PostgreSQL « undefined_table ». */
    private static final String UNDEFINED_TABLE = "42P01";

    private static final String COLS =
            "id, kind, opportunity_id, name, strategy, predicted_score, expected_irr, expected_moic, "
                    + "outcome, realized_irr, realized_moic, realized_dpi, note";

    /** Tous les résultats enregistrés (jeu de données de calibration). Vide si non migré. */
    public List<Outcome> findAll() {
        List<Outcome> result = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM opportunity_outcome ORDER BY recorded_at DESC";
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            if (UNDEFINED_TABLE.equals(e.getSQLState())) {
                return List.of();   // table absente (Neon non migré) : pas de données
            }
            throw new IllegalStateException("Lecture de opportunity_outcome impossible", e);
        }
        return result;
    }

    /** Résultat déjà saisi pour une opportunité, ou {@code null}. */
    public Outcome find(String kind, long opportunityId) {
        String sql = "SELECT " + COLS + " FROM opportunity_outcome WHERE kind = ? AND opportunity_id = ?";
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, kind);
            ps.setLong(2, opportunityId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            if (UNDEFINED_TABLE.equals(e.getSQLState())) {
                return null;
            }
            throw new IllegalStateException("Lecture de opportunity_outcome impossible", e);
        }
    }

    /** Insère ou met à jour le résultat d'une opportunité (clé kind + opportunity_id). */
    public void upsert(Outcome o, long userId) {
        String sql = """
                INSERT INTO opportunity_outcome
                  (kind, opportunity_id, name, strategy, predicted_score, expected_irr, expected_moic,
                   outcome, realized_irr, realized_moic, realized_dpi, note, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (kind, opportunity_id) DO UPDATE SET
                   outcome = EXCLUDED.outcome,
                   realized_irr = EXCLUDED.realized_irr,
                   realized_moic = EXCLUDED.realized_moic,
                   realized_dpi = EXCLUDED.realized_dpi,
                   note = EXCLUDED.note,
                   updated_at = now(),
                   updated_by = EXCLUDED.updated_by
                """;
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, o.kind());
            ps.setLong(2, o.opportunityId());
            ps.setString(3, o.name());
            JdbcSupport.setString(ps, 4, o.strategy());
            JdbcSupport.setInteger(ps, 5, o.predictedScore());
            JdbcSupport.setDouble(ps, 6, o.expectedIrr());
            JdbcSupport.setDouble(ps, 7, o.expectedMoic());
            JdbcSupport.setString(ps, 8, o.outcome() == null ? null : o.outcome().name());
            JdbcSupport.setDouble(ps, 9, o.realizedIrr());
            JdbcSupport.setDouble(ps, 10, o.realizedMoic());
            JdbcSupport.setDouble(ps, 11, o.realizedDpi());
            JdbcSupport.setString(ps, 12, o.note());
            ps.setLong(13, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (UNDEFINED_TABLE.equals(e.getSQLState())) {
                throw new IllegalStateException(
                        "Outcome storage is not available yet. The database migration "
                                + "(opportunity_outcome table) has not been applied.");
            }
            throw new IllegalStateException("Enregistrement de opportunity_outcome impossible", e);
        }
    }

    private Outcome map(ResultSet rs) throws SQLException {
        String state = rs.getString("outcome");
        return new Outcome(
                JdbcSupport.getLong(rs, "id"),
                rs.getString("kind"),
                rs.getLong("opportunity_id"),
                rs.getString("name"),
                rs.getString("strategy"),
                JdbcSupport.getInteger(rs, "predicted_score"),
                JdbcSupport.getDouble(rs, "expected_irr"),
                JdbcSupport.getDouble(rs, "expected_moic"),
                state == null ? null : OutcomeState.valueOf(state),
                JdbcSupport.getDouble(rs, "realized_irr"),
                JdbcSupport.getDouble(rs, "realized_moic"),
                JdbcSupport.getDouble(rs, "realized_dpi"),
                rs.getString("note"));
    }
}
