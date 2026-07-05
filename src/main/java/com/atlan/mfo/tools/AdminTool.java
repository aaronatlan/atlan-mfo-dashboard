package com.atlan.mfo.tools;

import com.atlan.mfo.auth.PasswordHasher;
import com.atlan.mfo.config.AppConfig;
import com.atlan.mfo.dao.UserDao;
import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.enums.Role;

import java.util.Arrays;

/**
 * Outil en ligne de commande pour provisionner un utilisateur applicatif
 * (production). Hache le mot de passe en BCrypt et l'insère (ou réinitialise
 * un compte existant). Le nouveau compte doit changer son mot de passe au
 * premier login.
 *
 * <p>Usage :
 * <pre>scripts/user-add.sh &lt;username&gt; &lt;mot_de_passe_temporaire&gt; "&lt;Nom complet&gt;" [ANALYST|PARTNER]</pre>
 *
 * La configuration base est lue comme par l'application (config.properties ou
 * variables d'environnement ATLAN_DB_*).
 */
public final class AdminTool {

    private AdminTool() {
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage : AdminTool <username> <mot_de_passe> \"<Nom complet>\" [ANALYST|PARTNER]");
            System.exit(2);
            return;
        }
        String username = args[0];
        char[] password = args[1].toCharArray();
        String fullName = args[2];
        Role role;
        try {
            role = args.length >= 4 ? Role.valueOf(args[3].toUpperCase()) : Role.ANALYST;
        } catch (IllegalArgumentException e) {
            System.err.println("Rôle inconnu : " + args[3] + " (attendu : ANALYST ou PARTNER)");
            System.exit(2);
            return;
        }

        AppConfig config = AppConfig.load();
        Database.init(config);
        try {
            String hash = PasswordHasher.hash(password);
            boolean created = new UserDao().upsertUser(username, hash, fullName, role, true);
            System.out.printf("%s : %s (%s) — changement de mot de passe requis au 1er login.%n",
                    created ? "Utilisateur créé" : "Utilisateur mis à jour", username, role);
        } finally {
            Arrays.fill(password, '\0');
            Database.close();
        }
    }
}
