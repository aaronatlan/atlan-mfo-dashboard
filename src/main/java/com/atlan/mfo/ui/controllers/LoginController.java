package com.atlan.mfo.ui.controllers;

import com.atlan.mfo.Main;
import com.atlan.mfo.auth.AuthService;
import com.atlan.mfo.model.AppUser;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.Arrays;
import java.util.Optional;

/** Contrôleur de l'écran de login. */
public class LoginController {

    private final AuthService authService;
    private final Main app;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    public LoginController(AuthService authService, Main app) {
        this.authService = authService;
        this.app = app;
    }

    @FXML
    private void initialize() {
        hideError();
    }

    @FXML
    private void onLogin() {
        hideError();
        String username = usernameField.getText();
        char[] password = passwordField.getText().toCharArray();
        try {
            Optional<AppUser> user = authService.login(username, password);
            if (user.isEmpty()) {
                showError("Identifiant ou mot de passe incorrect.");
                passwordField.clear();
                return;
            }
            AppUser u = user.get();
            if (u.mustChangePassword()) {
                app.showChangePassword(u);
            } else {
                app.showHome(u);
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
