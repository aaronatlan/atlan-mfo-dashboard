package com.atlan.mfo.db;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sauvegarde automatique périodique de toute la base — filet de sécurité local,
 * indépendant de la fenêtre de restauration Neon (6h sur le plan gratuit, voir §6
 * DEPLOYMENT.md). Écrit un dump SQL horodaté (INSERT autonomes, restaurables via
 * {@code psql -f}, sans dépendance à {@code pg_dump}) dans {@code ~/.atlan-mfo/backups/},
 * à l'ouverture de l'app puis toutes les {@link #INTERVAL_HOURS} heures ; conserve les
 * {@link #KEEP} plus récentes et supprime les plus anciennes.
 *
 * <p>Best-effort : une erreur (disque plein, base injoignable…) est journalisée sur
 * stderr, jamais remontée à l'utilisateur ni ne bloque l'application — une sauvegarde
 * ratée ne doit jamais empêcher de travailler.
 */
public final class BackupScheduler {

    // Ordre FK-safe : app_user avant les tables qui la référencent (updated_by),
    // fund_investment avant fund_vintage.
    private static final List<String> TABLES = List.of(
            "app_user", "fund_investment", "fund_vintage", "direct_deal",
            "scoring_param", "fx_rate", "opportunity_outcome");

    private static final long INTERVAL_HOURS = 4;   // < fenêtre PITR Neon (6h) : chevauchement de sécurité
    private static final int KEEP = 60;             // ~10 jours d'historique au rythme ci-dessus

    private static ScheduledExecutorService executor;

    private BackupScheduler() {
    }

    /** Démarre les sauvegardes périodiques (immédiate, puis toutes les {@code INTERVAL_HOURS} h). Idempotent. */
    public static synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);   // ne retient jamais la JVM ouverte
            return t;
        });
        executor.scheduleAtFixedRate(BackupScheduler::runSafely, 0, INTERVAL_HOURS, TimeUnit.HOURS);
    }

    /** Arrête le planificateur (appelé à la fermeture de l'application). */
    public static synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static void runSafely() {
        try {
            Path dir = backupDir();
            Files.createDirectories(dir);
            Path file = writeBackup(dir);
            prune(dir);
            System.out.println("[backup] écrite : " + file);
        } catch (Exception e) {
            System.err.println("[backup] échec (ignoré, l'application continue) : " + e.getMessage());
        }
    }

    private static Path backupDir() {
        return Path.of(System.getProperty("user.home"), ".atlan-mfo", "backups");
    }

    private static Path writeBackup(Path dir) throws SQLException, IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve("backup-" + ts + ".sql");
        try (Connection conn = Database.dataSource().getConnection();
             BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("-- Patrimium MFO — sauvegarde automatique (" + ts + ")\n");
            w.write("-- Restauration : psql <connexion> -f " + file.getFileName() + "\n");
            w.write("BEGIN;\n");
            for (String table : TABLES) {
                SqlDump.dumpTable(conn, table, w);
            }
            w.write("COMMIT;\n");
        }
        return file;
    }

    /** Ne conserve que les {@code KEEP} sauvegardes les plus récentes (les plus anciennes sont supprimées). */
    private static void prune(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            List<Path> sorted = files
                    .filter(p -> p.getFileName().toString().startsWith("backup-")
                            && p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            for (int i = KEEP; i < sorted.size(); i++) {
                Files.deleteIfExists(sorted.get(i));
            }
        }
    }
}
