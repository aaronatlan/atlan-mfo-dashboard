package com.atlan.mfo.dao;

import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.enums.BenchmarkStatus;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.util.JdbcSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Accès en lecture aux deals directs (table {@code direct_deal}). */
public final class DirectDealDao {

    private static final String SELECT = """
            SELECT id, name, next_steps, status, vs_benchmark, industry, gp, geography, inv_type, commitment,
                   revenue, cagr_pct, ebitda, ebitda_gr_pct, ebitda_mgn_pct, fcf, fcf_conv_pct, ev,
                   entry_mult, peers_mult, exit_val, exp_irr_pct, exp_moic,
                   deal_deadline, target_exit, comments,
                   contact_name, contact_email, contact_phone, currency,
                   asset_class, sub_strategy, access_route, secondary_mandate, underlying_strategy,
                   score_snapshot, sub_cagr, sub_ebitda_mgn, sub_fcf, sub_irr, sub_geo, sub_time,
                   version, updated_at, updated_by
              FROM direct_deal
            """;

    public List<DirectDeal> findAll() {
        List<DirectDeal> result = new ArrayList<>();
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT + " ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture de direct_deal impossible", e);
        }
        return result;
    }

    /* ---- Écritures (Phase 3) ---- */

    private static final String INSERT = """
            INSERT INTO direct_deal
              (name, next_steps, status, vs_benchmark, industry, gp, geography, inv_type, commitment,
               revenue, cagr_pct, ebitda, ebitda_gr_pct, ebitda_mgn_pct, fcf, fcf_conv_pct, ev,
               entry_mult, peers_mult, exit_val, exp_irr_pct, exp_moic,
               deal_deadline, target_exit, comments,
               score_snapshot, sub_cagr, sub_ebitda_mgn, sub_fcf, sub_irr, sub_geo, sub_time,
               contact_name, contact_email, contact_phone, currency,
               asset_class, sub_strategy, access_route, secondary_mandate, underlying_strategy, updated_by)
            VALUES (?, ?, ?::deal_status, ?::benchmark_status, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String UPDATE = """
            UPDATE direct_deal SET
               name=?, next_steps=?, status=?::deal_status, vs_benchmark=?::benchmark_status,
               industry=?, gp=?, geography=?, inv_type=?, commitment=?,
               revenue=?, cagr_pct=?, ebitda=?, ebitda_gr_pct=?, ebitda_mgn_pct=?, fcf=?, fcf_conv_pct=?, ev=?,
               entry_mult=?, peers_mult=?, exit_val=?, exp_irr_pct=?, exp_moic=?,
               deal_deadline=?, target_exit=?, comments=?,
               score_snapshot=?, sub_cagr=?, sub_ebitda_mgn=?, sub_fcf=?, sub_irr=?, sub_geo=?, sub_time=?,
               contact_name=?, contact_email=?, contact_phone=?, currency=?,
               asset_class=?, sub_strategy=?, access_route=?, secondary_mandate=?, underlying_strategy=?,
               version=version+1, updated_at=now(), updated_by=?
             WHERE id=? AND version=?
            """;

    /** Crée un deal direct. Renvoie l'id généré. */
    public long insert(DirectDeal d, ScoreBreakdown score, long userId) {
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT)) {
            setDealParams(ps, d, score);
            ps.setLong(42, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Création du deal impossible", e);
        }
    }

    /** Change uniquement le statut (décision prise en comité, mode présentation §6.3). */
    public void updateStatus(long id, com.atlan.mfo.model.enums.DealStatus status, long userId) {
        String sql = """
                UPDATE direct_deal
                   SET status = ?::deal_status, version = version + 1, updated_at = now(), updated_by = ?
                 WHERE id = ?
                """;
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, userId);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Changement de statut du deal impossible", e);
        }
    }

    /** Supprime un deal direct. */
    public void delete(long id) {
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM direct_deal WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Suppression du deal impossible", e);
        }
    }

    /**
     * Met à jour un deal sous verrou optimiste (§13.2).
     *
     * @throws StaleDataException si la fiche a été modifiée entre-temps.
     */
    public void update(DirectDeal d, ScoreBreakdown score, long userId) {
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE)) {
            setDealParams(ps, d, score);
            ps.setLong(42, userId);
            ps.setLong(43, d.id());
            ps.setLong(44, d.version());
            if (ps.executeUpdate() == 0) {
                throw new StaleDataException(
                        "The deal has been modified by another user since it was opened.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Mise à jour du deal impossible", e);
        }
    }

    /** Renseigne les 32 colonnes de données (1..32). L'appelant fixe updated_by / id / version. */
    private void setDealParams(PreparedStatement ps, DirectDeal d, ScoreBreakdown s) throws SQLException {
        ps.setString(1, d.name());
        JdbcSupport.setString(ps, 2, d.nextSteps());
        ps.setString(3, d.status().name());
        JdbcSupport.setString(ps, 4, d.vsBenchmark() == null ? null : d.vsBenchmark().name());
        JdbcSupport.setString(ps, 5, d.industry());
        JdbcSupport.setString(ps, 6, d.gp());
        JdbcSupport.setString(ps, 7, d.geography());
        JdbcSupport.setString(ps, 8, d.invType());
        JdbcSupport.setDouble(ps, 9, d.commitment());
        JdbcSupport.setDouble(ps, 10, d.revenue());
        JdbcSupport.setDouble(ps, 11, d.cagrPct());
        JdbcSupport.setDouble(ps, 12, d.ebitda());
        JdbcSupport.setDouble(ps, 13, d.ebitdaGrPct());
        JdbcSupport.setDouble(ps, 14, d.ebitdaMgnPct());
        JdbcSupport.setDouble(ps, 15, d.fcf());
        JdbcSupport.setDouble(ps, 16, d.fcfConvPct());
        JdbcSupport.setDouble(ps, 17, d.ev());
        JdbcSupport.setDouble(ps, 18, d.entryMult());
        JdbcSupport.setString(ps, 19, d.peersMult());
        JdbcSupport.setDouble(ps, 20, d.exitVal());
        JdbcSupport.setDouble(ps, 21, d.expIrrPct());
        JdbcSupport.setDouble(ps, 22, d.expMoic());
        JdbcSupport.setDate(ps, 23, d.dealDeadline());
        JdbcSupport.setDate(ps, 24, d.targetExit());
        JdbcSupport.setString(ps, 25, d.comments());
        JdbcSupport.setInteger(ps, 26, s.score());
        JdbcSupport.setDouble(ps, 27, s.subScoreOf("Revenue CAGR"));
        JdbcSupport.setDouble(ps, 28, s.subScoreOf("EBITDA Margin"));
        JdbcSupport.setDouble(ps, 29, s.subScoreOf("FCF Conversion"));
        JdbcSupport.setDouble(ps, 30, s.subScoreOf("Expected IRR"));
        JdbcSupport.setDouble(ps, 31, s.subScoreOf("Geography"));
        JdbcSupport.setDouble(ps, 32, s.subScoreOf("Timeline"));
        JdbcSupport.setString(ps, 33, d.contactName());
        JdbcSupport.setString(ps, 34, d.contactEmail());
        JdbcSupport.setString(ps, 35, d.contactPhone());
        ps.setString(36, d.currency() == null ? "USD" : d.currency());
        JdbcSupport.setString(ps, 37, d.assetClass());
        JdbcSupport.setString(ps, 38, d.subStrategy());
        JdbcSupport.setString(ps, 39, d.accessRoute());
        JdbcSupport.setString(ps, 40, d.secondaryMandate());
        JdbcSupport.setString(ps, 41, d.underlyingStrategy());
    }

    private DirectDeal map(ResultSet rs) throws SQLException {
        String bench = rs.getString("vs_benchmark");
        return new DirectDeal(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("next_steps"),
                DealStatus.valueOf(rs.getString("status")),
                bench == null ? null : BenchmarkStatus.valueOf(bench),
                rs.getString("industry"),
                rs.getString("gp"),
                rs.getString("geography"),
                rs.getString("inv_type"),
                JdbcSupport.getDouble(rs, "commitment"),

                JdbcSupport.getDouble(rs, "revenue"),
                JdbcSupport.getDouble(rs, "cagr_pct"),
                JdbcSupport.getDouble(rs, "ebitda"),
                JdbcSupport.getDouble(rs, "ebitda_gr_pct"),
                JdbcSupport.getDouble(rs, "ebitda_mgn_pct"),
                JdbcSupport.getDouble(rs, "fcf"),
                JdbcSupport.getDouble(rs, "fcf_conv_pct"),
                JdbcSupport.getDouble(rs, "ev"),

                JdbcSupport.getDouble(rs, "entry_mult"),
                rs.getString("peers_mult"),
                JdbcSupport.getDouble(rs, "exit_val"),
                JdbcSupport.getDouble(rs, "exp_irr_pct"),
                JdbcSupport.getDouble(rs, "exp_moic"),

                JdbcSupport.getLocalDate(rs, "deal_deadline"),
                JdbcSupport.getLocalDate(rs, "target_exit"),

                rs.getString("comments"),

                JdbcSupport.getInteger(rs, "score_snapshot"),
                JdbcSupport.getDouble(rs, "sub_cagr"),
                JdbcSupport.getDouble(rs, "sub_ebitda_mgn"),
                JdbcSupport.getDouble(rs, "sub_fcf"),
                JdbcSupport.getDouble(rs, "sub_irr"),
                JdbcSupport.getDouble(rs, "sub_geo"),
                JdbcSupport.getDouble(rs, "sub_time"),

                rs.getLong("version"),
                JdbcSupport.getOffsetDateTime(rs, "updated_at"),
                JdbcSupport.getLong(rs, "updated_by"),

                rs.getString("contact_name"),
                rs.getString("contact_email"),
                rs.getString("contact_phone"),

                rs.getString("currency"),

                rs.getString("asset_class"),
                rs.getString("sub_strategy"),
                rs.getString("access_route"),
                rs.getString("secondary_mandate"),
                rs.getString("underlying_strategy"));
    }
}
