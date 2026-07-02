package com.atlan.mfo.auth;

import com.atlan.mfo.dao.UserDao;
import com.atlan.mfo.model.AppUser;

import java.util.Optional;

/**
 * Service d'authentification : vérification des identifiants contre
 * {@code app_user} (hash BCrypt) et changement de mot de passe.
 */
public final class AuthService {

    private final UserDao userDao;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * Authentifie un utilisateur.
     *
     * @return l'utilisateur si identifiants valides et compte actif, sinon vide.
     */
    public Optional<AppUser> login(String username, char[] password) {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            return Optional.empty();
        }
        Optional<UserCredentials> found = userDao.findAuthByUsername(username.trim());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UserCredentials creds = found.get();
        if (!creds.user().active()) {
            return Optional.empty();
        }
        if (!PasswordHasher.verify(password, creds.passwordHash())) {
            return Optional.empty();
        }
        return Optional.of(creds.user());
    }

    /**
     * Change le mot de passe d'un utilisateur et lève le drapeau
     * {@code must_change_password}.
     */
    public void changePassword(long userId, char[] newPassword) {
        String hash = PasswordHasher.hash(newPassword);
        userDao.updatePassword(userId, hash);
    }
}
