package com.atlan.mfo.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration de l'application.
 *
 * <p>Priorité : variables d'environnement (ATLAN_DB_URL, ATLAN_DB_USER,
 * ATLAN_DB_PASSWORD) puis, à défaut, un fichier local {@code config.properties}
 * (jamais versionné). Aucun secret n'est écrit en dur dans le code.
 */
public final class AppConfig {

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final boolean runMigrations;
    private final String seedProfile;

    private AppConfig(String dbUrl, String dbUser, String dbPassword, boolean runMigrations, String seedProfile) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.runMigrations = runMigrations;
        this.seedProfile = seedProfile;
    }

    /** Charge la configuration depuis l'environnement puis config.properties. */
    public static AppConfig load() {
        Properties props = loadPropertiesFile();

        String url = firstNonBlank(System.getenv("ATLAN_DB_URL"), props.getProperty("db.url"));
        String user = firstNonBlank(System.getenv("ATLAN_DB_USER"), props.getProperty("db.user"));
        String password = firstNonBlank(System.getenv("ATLAN_DB_PASSWORD"), props.getProperty("db.password"));
        boolean migrations = Boolean.parseBoolean(
                firstNonBlank(props.getProperty("db.runMigrations"), "true"));
        // Profil de seed : dev (démo, défaut) | prod (admin seul) | none (aucun)
        String seed = firstNonBlank(System.getenv("ATLAN_DB_SEED"),
                firstNonBlank(props.getProperty("db.seed"), "dev")).toLowerCase();
        if (!seed.equals("dev") && !seed.equals("prod") && !seed.equals("none")) {
            throw new IllegalStateException("db.seed doit valoir dev, prod ou none (reçu : " + seed + ")");
        }

        if (isBlank(url)) {
            throw new IllegalStateException(
                    "Configuration base absente : définir ATLAN_DB_URL ou db.url dans config.properties "
                            + "(copier config.properties.example).");
        }

        return new AppConfig(url, user == null ? "" : user, password == null ? "" : password, migrations, seed);
    }

    private static Properties loadPropertiesFile() {
        Properties props = new Properties();
        Path local = Path.of("config.properties");
        if (Files.exists(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Lecture de config.properties impossible", e);
            }
        }
        return props;
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : b;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public String dbUrl() {
        return dbUrl;
    }

    public String dbUser() {
        return dbUser;
    }

    public String dbPassword() {
        return dbPassword;
    }

    public boolean runMigrations() {
        return runMigrations;
    }

    /** Profil de seed : {@code dev} | {@code prod} | {@code none}. */
    public String seedProfile() {
        return seedProfile;
    }
}
