package com.atlan.mfo.ui.controllers;

import com.atlan.mfo.Main;
import com.atlan.mfo.auth.AuthService;
import com.atlan.mfo.model.AppUser;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

import java.util.Arrays;

/**
 * Contrôleur du changement de mot de passe forcé au premier login (voir §13.3).
 */
public class ChangePasswordController {

    private static final int MIN_LENGTH = 8;

    private final AuthService authService;
    private final Main app;
    private final AppUser user;

    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;

    public ChangePasswordController(AuthService authService, Main app, AppUser user) {
        this.authService = authService;
        this.app = app;
        this.user = user;
    }

    @FXML
    private void initialize() {
        hideError();
    }

    @FXML
    private void onSubmit() {
        hideError();
        String pwd = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (pwd == null || pwd.length() < MIN_LENGTH) {
            showError("Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères.");
            return;
        }
        if (!pwd.equals(confirm)) {
            showError("Les deux mots de passe ne correspondent pas.");
            return;
        }

        char[] chars = pwd.toCharArray();
        try {
            authService.changePassword(user.id(), chars);
        } finally {
            Arrays.fill(chars, '\0');
        }

        AppUser updated = new AppUser(
                user.id(), user.username(), user.fullName(),
                user.role(), user.active(), false);
        app.showHome(updated);
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
