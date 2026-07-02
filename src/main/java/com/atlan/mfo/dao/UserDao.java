package com.atlan.mfo.dao;

import com.atlan.mfo.auth.UserCredentials;
import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.AppUser;
import com.atlan.mfo.model.enums.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Accès aux données des utilisateurs applicatifs (table {@code app_user}). */
public final class UserDao {

    /** Recherche un utilisateur et son hash par identifiant. */
    public Optional<UserCredentials> findAuthByUsername(String username) {
        String sql = """
                SELECT id, username, password_hash, full_name, role, active, must_change_password
                  FROM app_user
                 WHERE username = ?
                """;
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                AppUser user = mapUser(rs);
                return Optional.of(new UserCredentials(user, rs.getString("password_hash")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture de app_user impossible", e);
        }
    }

    /** Met à jour le mot de passe et lève le drapeau must_change_password. */
    public void updatePassword(long userId, String newHash) {
        String sql = """
                UPDATE app_user
                   SET password_hash = ?, must_change_password = FALSE
                 WHERE id = ?
                """;
        try (Connection conn = Database.dataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setLong(2, userId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalStateException("Utilisateur introuvable (id=" + userId + ")");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Mise à jour du mot de passe impossible", e);
        }
    }

    private AppUser mapUser(ResultSet rs) throws SQLException {
        return new AppUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("full_name"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("active"),
                rs.getBoolean("must_change_password"));
    }
}
