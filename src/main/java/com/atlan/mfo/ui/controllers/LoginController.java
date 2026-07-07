package com.atlan.mfo.ui.controllers;

import com.atlan.mfo.Main;
import com.atlan.mfo.auth.AuthService;
import com.atlan.mfo.model.AppUser;
import com.atlan.mfo.ui.util.Async;
import com.atlan.mfo.ui.util.ErrorDialog;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
    @FXML private Button loginButton;
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
        // Authentification (BCrypt + accès base) hors thread UI : l'interface ne fige pas.
        setBusy(true);
        Async.run(
                () -> authService.login(username, password),
                (Optional<AppUser> user) -> {
                    Arrays.fill(password, '\0');
                    setBusy(false);
                    if (user.isEmpty()) {
                        showError("Incorrect username or password.");
                        passwordField.clear();
                        return;
                    }
                    AppUser u = user.get();
                    if (u.mustChangePassword()) {
                        app.showChangePassword(u);
                    } else {
                        app.showHome(u);
                    }
                },
                ex -> {
                    Arrays.fill(password, '\0');
                    setBusy(false);
                    ErrorDialog.show(ex);
                });
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        loginButton.setText(busy ? "Signing in…" : "Log in");
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
