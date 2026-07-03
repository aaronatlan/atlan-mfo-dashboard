package com.atlan.mfo;

import com.atlan.mfo.auth.AuthService;
import com.atlan.mfo.auth.Session;
import com.atlan.mfo.config.AppConfig;
import com.atlan.mfo.dao.UserDao;
import com.atlan.mfo.db.Database;
import com.atlan.mfo.db.Migrations;
import com.atlan.mfo.model.AppUser;
import com.atlan.mfo.ui.controllers.ChangePasswordController;
import com.atlan.mfo.ui.controllers.LoginController;
import com.atlan.mfo.ui.controllers.MainShellController;
import com.atlan.mfo.ui.util.PipelineLoader;
import com.atlan.mfo.ui.view.PresentationView;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Point d'entrée JavaFX. Initialise la base, exécute les migrations puis pilote
 * la navigation entre les écrans (login → changement de mot de passe → accueil).
 */
public class Main extends Application {

    private static final String CSS = "/css/atlan-dark.css";

    private Stage stage;
    private AuthService authService;

    @Override
    public void init() {
        AppConfig config = AppConfig.load();
        Database.init(config);
        if (config.runMigrations()) {
            Migrations.run(config.seedProfile());
        }
        this.authService = new AuthService(new UserDao());
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        loadFonts();
        stage.setTitle("Atlan MFO Dashboard");
        showLogin();
        stage.show();
    }

    @Override
    public void stop() {
        Database.close();
    }

    /* ---- Navigation ---- */

    public void showLogin() {
        LoginController controller = new LoginController(authService, this);
        Parent root = load("/fxml/login.fxml", controller);
        setScene(root, 420, 520);
    }

    public void showChangePassword(AppUser user) {
        ChangePasswordController controller = new ChangePasswordController(authService, this, user);
        Parent root = load("/fxml/change-password.fxml", controller);
        setScene(root, 420, 520);
    }

    /**
     * Après login : un partner atterrit (et reste) en mode présentation ; un
     * analyste atterrit dans la coquille éditable (voir §7).
     */
    public void showHome(AppUser user) {
        Session.setCurrentUser(user);
        if (user.isPartner()) {
            showPresentation(user);
        } else {
            showAnalystShell(user);
        }
    }

    /** Coquille analyste (menu latéral + Pipeline summary, édition). */
    public void showAnalystShell(AppUser user) {
        stage.setFullScreen(false);
        MainShellController controller = new MainShellController(this, user);
        Parent root = load("/fxml/main.fxml", controller);
        setScene(root, 1240, 780);
    }

    /**
     * Mode présentation (§6.3). L'analyste peut revenir à sa vue ; le partner est
     * verrouillé ici (pas de retour), les deux peuvent passer en plein écran.
     */
    public void showPresentation(AppUser user) {
        Runnable onExitToAnalyst = user.isAnalyst() ? () -> showAnalystShell(user) : null;
        Runnable onFullScreen = () -> stage.setFullScreen(!stage.isFullScreen());
        Runnable onLogout = () -> {
            Session.clear();
            showLogin();
        };
        PresentationView view = new PresentationView(
                PipelineLoader.loadItems(), onExitToAnalyst, onFullScreen, onLogout);
        setScene(view, 1280, 800);
    }

    /* ---- Helpers ---- */

    private void setScene(Parent root, double w, double h) {
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root, w, h);
            applyStylesheet(scene);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        stage.setWidth(w);
        stage.setHeight(h);
        stage.centerOnScreen();
    }

    private Parent load(String fxml, Object controller) {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource(fxml), "FXML introuvable : " + fxml));
        loader.setController(controller);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Chargement de " + fxml + " impossible", e);
        }
    }

    private void applyStylesheet(Scene scene) {
        var url = getClass().getResource(CSS);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }

    /** Charge les polices embarquées si présentes (fallback système sinon). */
    private void loadFonts() {
        for (String path : new String[]{
                "/fonts/Inter-Regular.ttf",
                "/fonts/Inter-SemiBold.ttf",
                "/fonts/Newsreader-Regular.ttf"}) {
            try (InputStream in = getClass().getResourceAsStream(path)) {
                if (in != null) {
                    Font.loadFont(in, 12);
                }
            } catch (IOException ignored) {
                // police absente : fallback système
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
