package com.atlan.mfo.db;

import com.atlan.mfo.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Point d'accès unique au pool de connexions HikariCP.
 *
 * <p>L'application se connecte avec un seul rôle applicatif ; le multi-utilisateur
 * est géré via la table {@code app_user} (voir §2).
 */
public final class Database {

    private static HikariDataSource dataSource;

    private Database() {
    }

    /** Initialise le pool à partir de la configuration. Idempotent. */
    public static synchronized void init(AppConfig config) {
        if (dataSource != null) {
            return;
        }
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.dbUrl());
        hc.setUsername(config.dbUser());
        hc.setPassword(config.dbPassword());
        hc.setPoolName("atlan-mfo-pool");
        hc.setMaximumPoolSize(8);
        // Garder quelques connexions chaudes : les lectures parallèles (fonds/deals)
        // réutilisent des connexions prêtes au lieu de payer un handshake TLS à chaque fois.
        hc.setMinimumIdle(3);
        hc.setConnectionTimeout(15_000);
        // Ne pas valider une connexion au démarrage : sinon l'app bloque le temps que
        // Neon (free tier, « scale to zero ») réveille son compute avant d'afficher le
        // login. La connexion se fait à la 1re requête ; le pool se réchauffe en fond.
        hc.setInitializationFailTimeout(-1);
        dataSource = new HikariDataSource(hc);
    }

    public static DataSource dataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("Database.init(...) doit être appelé avant dataSource().");
        }
        return dataSource;
    }

    /** Ferme le pool (arrêt de l'application). */
    public static synchronized void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
