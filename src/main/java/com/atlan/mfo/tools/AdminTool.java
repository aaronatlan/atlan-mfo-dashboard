package com.atlan.mfo.tools;

import com.atlan.mfo.auth.PasswordHasher;
import com.atlan.mfo.config.AppConfig;
import com.atlan.mfo.dao.UserDao;
import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.enums.Role;

import java.util.Arrays;

/**
 * Command-line tool to provision an application user (production). Hashes
 * the password with BCrypt and inserts it (or resets an existing account).
 * The new account must change its password on first login.
 *
 * <p>Usage:
 * <pre>scripts/user-add.sh &lt;username&gt; &lt;temporary_password&gt; "&lt;Full name&gt;" [ANALYST|PARTNER]</pre>
 *
 * The database configuration is read the same way as the application
 * (config.properties or ATLAN_DB_* environment variables).
 */
public final class AdminTool {

    private AdminTool() {
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: AdminTool <username> <password> \"<Full name>\" [ANALYST|PARTNER]");
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
            System.err.println("Unknown role: " + args[3] + " (expected: ANALYST or PARTNER)");
            System.exit(2);
            return;
        }

        AppConfig config = AppConfig.load();
        Database.init(config);
        try {
            String hash = PasswordHasher.hash(password);
            boolean created = new UserDao().upsertUser(username, hash, fullName, role, true);
            System.out.printf("%s: %s (%s) — password change required on first login.%n",
                    created ? "User created" : "User updated", username, role);
        } finally {
            Arrays.fill(password, '\0');
            Database.close();
        }
    }
}
