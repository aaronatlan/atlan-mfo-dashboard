package com.atlan.mfo.dao;

import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.util.JdbcSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Accès en lecture aux millésimes des fonds (table {@code fund_vintage}). */
public final class FundVintageDao {

    private static final String SELECT = """
            SELECT id, fund_id, vintage_year, dpi, tvpi, irr, moic
              FROM fund_vintage
             ORDER BY fund_id, vintage_year DESC
            """;

    /** Tous les millésimes, groupés par fonds (plus récent d'abord). Évite le N+1. */
    public Map<Long, List<FundVintage>> findAllByFund() {
        Map<Long, List<FundVintage>> byFund = new LinkedHashMap<>();
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                FundVintage v = map(rs);
                byFund.computeIfAbsent(v.fundId(), k -> new ArrayList<>()).add(v);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture de fund_vintage impossible", e);
        }
        return byFund;
    }

    /** Insère un millésime dans la transaction du fonds (connexion partagée). */
    public void insert(Connection conn, long fundId, FundVintage v) throws SQLException {
        String sql = """
                INSERT INTO fund_vintage (fund_id, vintage_year, dpi, tvpi, irr, moic)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fundId);
            ps.setInt(2, v.vintageYear());
            JdbcSupport.setDouble(ps, 3, v.dpi());
            JdbcSupport.setDouble(ps, 4, v.tvpi());
            JdbcSupport.setDouble(ps, 5, v.irr());
            JdbcSupport.setDouble(ps, 6, v.moic());
            ps.executeUpdate();
        }
    }

    /** Supprime tous les millésimes d'un fonds (avant réinsertion à l'enregistrement). */
    public void deleteByFund(Connection conn, long fundId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM fund_vintage WHERE fund_id = ?")) {
            ps.setLong(1, fundId);
            ps.executeUpdate();
        }
    }

    private FundVintage map(ResultSet rs) throws SQLException {
        return new FundVintage(
                rs.getLong("id"),
                rs.getLong("fund_id"),
                rs.getInt("vintage_year"),
                JdbcSupport.getDouble(rs, "dpi"),
                JdbcSupport.getDouble(rs, "tvpi"),
                JdbcSupport.getDouble(rs, "irr"),
                JdbcSupport.getDouble(rs, "moic"));
    }
}
