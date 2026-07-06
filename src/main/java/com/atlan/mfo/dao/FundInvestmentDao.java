package com.atlan.mfo.dao;

import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.enums.BenchmarkStatus;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.util.JdbcSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Accès en lecture aux fonds (table {@code fund_investment}), millésimes attachés. */
public final class FundInvestmentDao {

    private final FundVintageDao vintageDao = new FundVintageDao();

    private static final String SELECT = """
            SELECT id, category, name, next_steps, status, vs_benchmark, geography, asset_class, commitment,
                   first_close, final_close, comments,
                   score_snapshot, sub_dpi, sub_irr, sub_moic, sub_geo, sub_time,
                   version, updated_at, updated_by
              FROM fund_investment
            """;

    public List<FundInvestment> findAll() {
        return query(SELECT + " ORDER BY name", null);
    }

    public List<FundInvestment> findByCategory(Category category) {
        return query(SELECT + " WHERE category = ?::category ORDER BY name", category.name());
    }

    private List<FundInvestment> query(String sql, String categoryParam) {
        Map<Long, List<FundVintage>> vintagesByFund = vintageDao.findAllByFund();
        List<FundInvestment> result = new ArrayList<>();
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (categoryParam != null) {
                ps.setString(1, categoryParam);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    result.add(map(rs, vintagesByFund.getOrDefault(id, List.of())));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture de fund_investment impossible", e);
        }
        return result;
    }

    /* ---- Écritures (Phase 3) ---- */

    private static final String INSERT = """
            INSERT INTO fund_investment
              (category, name, next_steps, status, vs_benchmark, geography, asset_class, commitment,
               first_close, final_close, comments,
               score_snapshot, sub_dpi, sub_irr, sub_moic, sub_geo, sub_time, updated_by)
            VALUES (?::category, ?, ?, ?::deal_status, ?::benchmark_status, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String UPDATE = """
            UPDATE fund_investment SET
               category=?::category, name=?, next_steps=?, status=?::deal_status, vs_benchmark=?::benchmark_status,
               geography=?, asset_class=?, commitment=?, first_close=?, final_close=?, comments=?,
               score_snapshot=?, sub_dpi=?, sub_irr=?, sub_moic=?, sub_geo=?, sub_time=?,
               version=version+1, updated_at=now(), updated_by=?
             WHERE id=? AND version=?
            """;

    /** Crée un fonds et ses millésimes (transaction). Renvoie l'id généré. */
    public long insert(FundInvestment f, ScoreBreakdown score, long userId) {
        try (Connection conn = Database.dataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = conn.prepareStatement(INSERT)) {
                    setFundParams(ps, f, score);
                    ps.setLong(18, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = rs.getLong(1);
                    }
                }
                for (FundVintage v : f.vintages()) {
                    vintageDao.insert(conn, id, v);
                }
                conn.commit();
                return id;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Création du fonds impossible", e);
        }
    }

    /** Change uniquement le statut (décision prise en comité, mode présentation §6.3). */
    public void updateStatus(long id, com.atlan.mfo.model.enums.DealStatus status, long userId) {
        String sql = """
                UPDATE fund_investment
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
            throw new IllegalStateException("Changement de statut du fonds impossible", e);
        }
    }

    /** Supprime un fonds (ses millésimes sont supprimés en cascade, voir §4.2). */
    public void delete(long id) {
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM fund_investment WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Suppression du fonds impossible", e);
        }
    }

    /**
     * Met à jour un fonds sous verrou optimiste (§13.2) et remplace ses millésimes.
     *
     * @throws StaleDataException si la fiche a été modifiée entre-temps.
     */
    public void update(FundInvestment f, ScoreBreakdown score, long userId) {
        try (Connection conn = Database.dataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(UPDATE)) {
                    setFundParams(ps, f, score);
                    ps.setLong(18, userId);
                    ps.setLong(19, f.id());
                    ps.setLong(20, f.version());
                    rows = ps.executeUpdate();
                }
                if (rows == 0) {
                    conn.rollback();
                    throw new StaleDataException(
                            "Le fonds a été modifié par un autre utilisateur depuis son ouverture.");
                }
                vintageDao.deleteByFund(conn, f.id());
                for (FundVintage v : f.vintages()) {
                    vintageDao.insert(conn, f.id(), v);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Mise à jour du fonds impossible", e);
        }
    }

    /** Renseigne les 17 colonnes de données (1..17). L'appelant fixe updated_by / id / version. */
    private void setFundParams(PreparedStatement ps, FundInvestment f, ScoreBreakdown s) throws SQLException {
        ps.setString(1, f.category().name());
        ps.setString(2, f.name());
        JdbcSupport.setString(ps, 3, f.nextSteps());
        ps.setString(4, f.status().name());
        JdbcSupport.setString(ps, 5, f.vsBenchmark() == null ? null : f.vsBenchmark().name());
        JdbcSupport.setString(ps, 6, f.geography());
        JdbcSupport.setString(ps, 7, f.assetClass());
        JdbcSupport.setDouble(ps, 8, f.commitment());
        JdbcSupport.setDate(ps, 9, f.firstClose());
        JdbcSupport.setDate(ps, 10, f.finalClose());
        JdbcSupport.setString(ps, 11, f.comments());
        JdbcSupport.setInteger(ps, 12, s.score());
        JdbcSupport.setDouble(ps, 13, s.subScoreOf("DPI"));
        JdbcSupport.setDouble(ps, 14, s.subScoreOf("IRR"));
        JdbcSupport.setDouble(ps, 15, s.subScoreOf("MOIC"));
        JdbcSupport.setDouble(ps, 16, s.subScoreOf("Géographie"));
        JdbcSupport.setDouble(ps, 17, s.subScoreOf("Timeline"));
    }

    private FundInvestment map(ResultSet rs, List<FundVintage> vintages) throws SQLException {
        String bench = rs.getString("vs_benchmark");
        return new FundInvestment(
                rs.getLong("id"),
                Category.valueOf(rs.getString("category")),
                rs.getString("name"),
                rs.getString("next_steps"),
                DealStatus.valueOf(rs.getString("status")),
                bench == null ? null : BenchmarkStatus.valueOf(bench),
                rs.getString("geography"),
                rs.getString("asset_class"),
                JdbcSupport.getDouble(rs, "commitment"),

                vintages,

                JdbcSupport.getLocalDate(rs, "first_close"),
                JdbcSupport.getLocalDate(rs, "final_close"),

                rs.getString("comments"),

                JdbcSupport.getInteger(rs, "score_snapshot"),
                JdbcSupport.getDouble(rs, "sub_dpi"),
                JdbcSupport.getDouble(rs, "sub_irr"),
                JdbcSupport.getDouble(rs, "sub_moic"),
                JdbcSupport.getDouble(rs, "sub_geo"),
                JdbcSupport.getDouble(rs, "sub_time"),

                rs.getLong("version"),
                JdbcSupport.getOffsetDateTime(rs, "updated_at"),
                JdbcSupport.getLong(rs, "updated_by"));
    }
}
