package com.atlan.mfo.dao;

import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.DirectDeal;
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
                JdbcSupport.getLong(rs, "updated_by"));
    }
}
