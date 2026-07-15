package com.atlan.mfo.db;

import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Sérialisation d'une table en instructions {@code INSERT}, restaurables via
 * {@code psql -f} (pas de dépendance à {@code pg_dump}). Partagé par
 * {@link com.atlan.mfo.tools.PipelineResetTool} (sauvegarde avant purge du pipeline) et
 * {@link BackupScheduler} (sauvegarde automatique complète de la base).
 */
public final class SqlDump {

    private SqlDump() {
    }

    /**
     * Écrit les {@code INSERT} de {@code table} dans {@code w}, triés par clé primaire
     * (découverte via les métadonnées JDBC : certaines tables n'ont pas de colonne
     * {@code id}, ex. {@code scoring_param.name}, {@code fx_rate.currency}).
     */
    public static void dumpTable(Connection conn, String table, BufferedWriter w)
            throws SQLException, IOException {
        String orderBy = primaryKeyOrderBy(conn, table);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + orderBy)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            StringBuilder cols = new StringBuilder();
            for (int i = 1; i <= n; i++) {
                if (i > 1) {
                    cols.append(", ");
                }
                cols.append(md.getColumnName(i));
            }
            int rows = 0;
            while (rs.next()) {
                StringBuilder vals = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) {
                        vals.append(", ");
                    }
                    vals.append(sqlLiteral(rs, md, i));
                }
                w.write("INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ");\n");
                rows++;
            }
            w.write("-- " + table + ": " + rows + " ligne(s)\n");
        }
    }

    /** Clause {@code ORDER BY} sur la clé primaire réelle de {@code table}, ou vide si absente. */
    private static String primaryKeyOrderBy(Connection conn, String table) throws SQLException {
        List<String> pk = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, null, table)) {
            while (rs.next()) {
                pk.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pk.isEmpty() ? "" : " ORDER BY " + String.join(", ", pk);
    }

    /** Littéral SQL d'une valeur, selon son type (nombres nus, booléens, sinon quoté). */
    private static String sqlLiteral(ResultSet rs, ResultSetMetaData md, int i) throws SQLException {
        if (rs.getObject(i) == null) {
            return "NULL";
        }
        switch (md.getColumnType(i)) {
            case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT,
                    Types.NUMERIC, Types.DECIMAL, Types.DOUBLE, Types.REAL, Types.FLOAT:
                return rs.getString(i);
            case Types.BOOLEAN, Types.BIT:
                return rs.getBoolean(i) ? "TRUE" : "FALSE";
            default:
                // chaînes, dates, timestamps, enums : quoté (cast implicite à l'insertion)
                return "'" + rs.getString(i).replace("'", "''") + "'";
        }
    }
}
