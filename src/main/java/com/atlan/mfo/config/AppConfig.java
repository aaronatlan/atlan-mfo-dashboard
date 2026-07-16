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
        // Défauts volontairement conservateurs : une configuration incomplète ne doit
        // JAMAIS toucher au schéma ni injecter de données. Un poste installé pointe sur
        // la base de production ; si l'opérateur oublie ces deux clés, les anciens défauts
        // (migrations=true, seed=dev) y auraient créé les comptes de démo admin/partner.
        // Le développement active explicitement les deux (voir config.properties.example).
        boolean migrations = Boolean.parseBoolean(
                firstNonBlank(System.getenv("ATLAN_DB_RUN_MIGRATIONS"),
                        firstNonBlank(props.getProperty("db.runMigrations"), "false")));
        // Profil de seed : dev (démo) | prod (admin seul) | none (aucun, défaut)
        String seed = firstNonBlank(System.getenv("ATLAN_DB_SEED"),
                firstNonBlank(props.getProperty("db.seed"), "none")).toLowerCase();
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

    /**
     * Cherche {@code config.properties} dans, par ordre de priorité :
     * <ol>
     *   <li>le répertoire courant (usage dev / lancement en ligne de commande) ;</li>
     *   <li>{@code ~/.atlan-mfo/config.properties} (emplacement stable pour un poste
     *       installé : indépendant du répertoire de travail, modifiable sans droits
     *       administrateur — cf. app native lancée depuis le menu Démarrer/Dock).</li>
     * </ol>
     * Le premier fichier trouvé est utilisé. Les variables d'environnement
     * {@code ATLAN_DB_*} restent prioritaires sur le contenu du fichier.
     */
    private static Properties loadPropertiesFile() {
        Properties props = new Properties();
        Path[] candidates = {
                Path.of("config.properties"),
                Path.of(System.getProperty("user.home"), ".atlan-mfo", "config.properties"),
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try (InputStream in = Files.newInputStream(candidate)) {
                    props.load(in);
                } catch (IOException e) {
                    throw new IllegalStateException("Lecture de " + candidate + " impossible", e);
                }
                break;
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
