package com.atlan.mfo.dao;

import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
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
                   contact_name, contact_email, contact_phone, currency,
                   sub_strategy, access_route, secondary_mandate, underlying_strategy, target_regions, gp_name,
                   version, updated_at, updated_by
              FROM fund_investment
            """;

    public List<FundInvestment> findAll() {
        return query(SELECT + " ORDER BY name");
    }

    private List<FundInvestment> query(String sql) {
        Map<Long, List<FundVintage>> vintagesByFund = vintageDao.findAllByFund();
        List<FundInvestment> result = new ArrayList<>();
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
               contact_name, contact_email, contact_phone, currency,
               sub_strategy, access_route, secondary_mandate, underlying_strategy, target_regions, gp_name, updated_by)
            VALUES (?::category, ?, ?, ?::deal_status, ?::benchmark_status, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String UPDATE = """
            UPDATE fund_investment SET
               category=?::category, name=?, next_steps=?, status=?::deal_status, vs_benchmark=?::benchmark_status,
               geography=?, asset_class=?, commitment=?, first_close=?, final_close=?, comments=?,
               contact_name=?, contact_email=?, contact_phone=?, currency=?,
               sub_strategy=?, access_route=?, secondary_mandate=?, underlying_strategy=?, target_regions=?, gp_name=?,
               version=version+1, updated_at=now(), updated_by=?
             WHERE id=? AND version=?
            """;

    /** Crée un fonds et ses millésimes (transaction). Renvoie l'id généré. */
    public long insert(FundInvestment f, long userId) {
        try (Connection conn = Database.dataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = conn.prepareStatement(INSERT)) {
                    setFundParams(ps, f);
                    ps.setLong(22, userId);
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
    public void update(FundInvestment f, long userId) {
        try (Connection conn = Database.dataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(UPDATE)) {
                    setFundParams(ps, f);
                    ps.setLong(22, userId);
                    ps.setLong(23, f.id());
                    ps.setLong(24, f.version());
                    rows = ps.executeUpdate();
                }
                if (rows == 0) {
                    conn.rollback();
                    throw new StaleDataException(
                            "The fund has been modified by another user since it was opened.");
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

    /** Renseigne les 21 colonnes de données (1..21). L'appelant fixe updated_by / id / version. */
    private void setFundParams(PreparedStatement ps, FundInvestment f) throws SQLException {
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
        JdbcSupport.setString(ps, 12, f.contactName());
        JdbcSupport.setString(ps, 13, f.contactEmail());
        JdbcSupport.setString(ps, 14, f.contactPhone());
        ps.setString(15, f.currency() == null ? "USD" : f.currency());
        JdbcSupport.setString(ps, 16, f.subStrategy());
        JdbcSupport.setString(ps, 17, f.accessRoute());
        JdbcSupport.setString(ps, 18, f.secondaryMandate());
        JdbcSupport.setString(ps, 19, f.underlyingStrategy());
        JdbcSupport.setString(ps, 20, f.targetRegions());
        JdbcSupport.setString(ps, 21, f.gpName());
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

                rs.getLong("version"),
                JdbcSupport.getOffsetDateTime(rs, "updated_at"),
                JdbcSupport.getLong(rs, "updated_by"),

                rs.getString("contact_name"),
                rs.getString("contact_email"),
                rs.getString("contact_phone"),

                rs.getString("currency"),

                rs.getString("sub_strategy"),
                rs.getString("access_route"),
                rs.getString("secondary_mandate"),
                rs.getString("underlying_strategy"),
                rs.getString("target_regions"),
                rs.getString("gp_name"));
    }
}
