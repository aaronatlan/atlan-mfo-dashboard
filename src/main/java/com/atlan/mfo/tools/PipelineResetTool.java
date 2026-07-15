package com.atlan.mfo.tools;

import com.atlan.mfo.config.AppConfig;
import com.atlan.mfo.db.Database;
import com.atlan.mfo.db.SqlDump;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

            // Sauvegarde AVANT toute suppression : filet de sécurité restaurable via psql.
            Path backup = backup(conn);
            System.out.println();
            System.out.println("Backup written: " + backup.toAbsolutePath());
            System.out.println("  Restore with: psql <connection> -f " + backup.getFileName());
            System.out.println();

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
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Pipeline reset failed", e);
        } finally {
            Database.close();
        }
    }

    /**
     * Exporte le pipeline (3 tables, ordre FK) en instructions {@code INSERT} dans un
     * fichier SQL horodaté, restaurable via {@code psql -f}. Autonome (n'exige pas
     * {@code pg_dump}) : réutilise la connexion applicative existante.
     */
    private static Path backup(Connection conn) throws SQLException, IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = Path.of("pipeline-backup-" + ts + ".sql");
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("-- Patrimium MFO — sauvegarde du pipeline avant réinitialisation (" + ts + ")\n");
            w.write("-- Restauration : psql <connexion> -f " + file.getFileName() + "\n");
            w.write("BEGIN;\n");
            SqlDump.dumpTable(conn, "fund_investment", w);   // avant fund_vintage (clé étrangère)
            SqlDump.dumpTable(conn, "fund_vintage", w);
            SqlDump.dumpTable(conn, "direct_deal", w);
            w.write("COMMIT;\n");
        }
        return file;
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
