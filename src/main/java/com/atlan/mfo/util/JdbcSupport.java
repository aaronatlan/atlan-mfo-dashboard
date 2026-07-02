package com.atlan.mfo.util;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Lecture de colonnes nullable depuis un {@link ResultSet} (boxed = null si SQL NULL). */
public final class JdbcSupport {

    private JdbcSupport() {
    }

    public static Integer getInteger(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    public static Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    public static Double getDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    public static LocalDate getLocalDate(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    public static OffsetDateTime getOffsetDateTime(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, OffsetDateTime.class);
    }
}
