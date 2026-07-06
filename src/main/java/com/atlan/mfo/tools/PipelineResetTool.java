package com.atlan.mfo.tools;

import com.atlan.mfo.config.AppConfig;
import com.atlan.mfo.db.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Vide le pipeline (fonds, millésimes, deals) après un comité d'investissement,
 * en conservant le schéma et les comptes utilisateurs ({@code app_user}).
 *
 * <p>Action destructive : n'exécute la purge que si l'argument {@code --yes} est
 * fourni (garde-fou machine ; la confirmation interactive est portée par
 * {@code scripts/reset-pipeline.sh}). Sans cet argument, affiche seulement l'état
 * actuel sans rien modifier.
 *
 * <p>Usage : {@code PipelineResetTool [--yes]}
 */
public final class PipelineResetTool {

    private PipelineResetTool() {
    }

    public static void main(String[] args) {
        boolean confirmed = args.length > 0 && "--yes".equals(args[0]);

        AppConfig config = AppConfig.load();
        Database.init(config);
        try (Connection conn = Database.dataSource().getConnection()) {
            printCounts(conn, "Current state");

            if (!confirmed) {
                System.out.println();
                System.out.println("No changes made (re-run with --yes to purge).");
                return;
            }

            // DELETE plutôt que TRUNCATE : le rôle applicatif de production (atlan_app)
            // n'a pas le privilège TRUNCATE (roles.sql, moindre privilège). Transaction
            // unique ; l'ordre respecte les FK (fund_vintage cascade via fund_investment).
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM fund_vintage");
                stmt.execute("DELETE FROM fund_investment");
                stmt.execute("DELETE FROM direct_deal");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

            System.out.println();
            System.out.println("Pipeline purged (fund_investment, fund_vintage, direct_deal).");
            System.out.println("User accounts (app_user) kept intact.");
            printCounts(conn, "State after purge");
        } catch (SQLException e) {
            throw new IllegalStateException("Pipeline reset failed", e);
        } finally {
            Database.close();
        }
    }

    private static void printCounts(Connection conn, String label) throws SQLException {
        System.out.println(label + ":");
        printCount(conn, "app_user");
        printCount(conn, "fund_investment");
        printCount(conn, "fund_vintage");
        printCount(conn, "direct_deal");
    }

    private static void printCount(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            System.out.printf("  %-16s %d%n", table, rs.getLong(1));
        }
    }
}
