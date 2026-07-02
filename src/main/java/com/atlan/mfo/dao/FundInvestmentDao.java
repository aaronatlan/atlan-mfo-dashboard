package com.atlan.mfo.dao;

import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.FundInvestment;
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

/** Accès en lecture aux fonds (table {@code fund_investment}). */
public final class FundInvestmentDao {

    private static final String SELECT = """
            SELECT id, category, name, next_steps, status, vs_benchmark, geography, asset_class, commitment,
                   recent_vintage, recent_dpi, recent_tvpi, recent_irr, recent_moic,
                   earlier_vintage, earlier_dpi, earlier_tvpi, earlier_irr, earlier_moic,
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
        List<FundInvestment> result = new ArrayList<>();
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (categoryParam != null) {
                ps.setString(1, categoryParam);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture de fund_investment impossible", e);
        }
        return result;
    }

    private FundInvestment map(ResultSet rs) throws SQLException {
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

                JdbcSupport.getInteger(rs, "recent_vintage"),
                JdbcSupport.getDouble(rs, "recent_dpi"),
                JdbcSupport.getDouble(rs, "recent_tvpi"),
                JdbcSupport.getDouble(rs, "recent_irr"),
                JdbcSupport.getDouble(rs, "recent_moic"),

                JdbcSupport.getInteger(rs, "earlier_vintage"),
                JdbcSupport.getDouble(rs, "earlier_dpi"),
                JdbcSupport.getDouble(rs, "earlier_tvpi"),
                JdbcSupport.getDouble(rs, "earlier_irr"),
                JdbcSupport.getDouble(rs, "earlier_moic"),

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
