package com.atlan.mfo.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Exécute les scripts SQL embarqués ({@code db/schema.sql} puis {@code db/seed.sql}).
 *
 * <p>Les scripts sont idempotents : ils peuvent être rejoués à chaque démarrage
 * sans effet de bord. Le driver PostgreSQL exécute plusieurs commandes séparées
 * par des points-virgules en un seul appel, y compris les blocs {@code DO $$ ... $$}.
 */
public final class Migrations {

    private static final String SCHEMA = "/db/schema.sql";
    private static final String SEED = "/db/seed.sql";

    private Migrations() {
    }

    /** Exécute schema.sql puis seed.sql sur le pool courant. */
    public static void run() {
        String schema = readResource(SCHEMA);
        String seed = readResource(SEED);
        try (Connection conn = Database.dataSource().getConnection()) {
            execute(conn, schema);
            execute(conn, seed);
        } catch (SQLException e) {
            throw new IllegalStateException("Échec de l'exécution des migrations", e);
        }
    }

    private static void execute(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static String readResource(String path) {
        try (InputStream in = Migrations.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Ressource SQL introuvable : " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Lecture de " + path + " impossible", e);
        }
    }
}
