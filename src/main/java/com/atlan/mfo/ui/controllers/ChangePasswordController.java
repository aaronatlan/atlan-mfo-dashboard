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
    @FXML private Button submitButton;
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
            showError("Password must be at least " + MIN_LENGTH + " characters long.");
            return;
        }
        if (!pwd.equals(confirm)) {
            showError("The two passwords do not match.");
            return;
        }

        char[] chars = pwd.toCharArray();
        // Changement (hachage BCrypt + accès base) hors thread UI.
        setBusy(true);
        Async.run(
                () -> authService.changePassword(user.id(), chars),
                () -> {
                    Arrays.fill(chars, '\0');
                    setBusy(false);
                    AppUser updated = new AppUser(
                            user.id(), user.username(), user.fullName(),
                            user.role(), user.active(), false);
                    app.showHome(updated);
                },
                ex -> {
                    Arrays.fill(chars, '\0');
                    setBusy(false);
                    ErrorDialog.show(ex);
                });
    }

    private void setBusy(boolean busy) {
        submitButton.setDisable(busy);
        newPasswordField.setDisable(busy);
        confirmPasswordField.setDisable(busy);
        submitButton.setText(busy ? "Saving…" : "Confirm");
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
