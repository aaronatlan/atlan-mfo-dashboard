package com.atlan.mfo;

import com.atlan.mfo.auth.AuthService;
import com.atlan.mfo.auth.Session;
import com.atlan.mfo.config.AppConfig;
import com.atlan.mfo.dao.DirectDealDao;
import com.atlan.mfo.dao.FundInvestmentDao;
import com.atlan.mfo.dao.ScoringConfig;
import com.atlan.mfo.dao.UserDao;
import com.atlan.mfo.db.Database;
import com.atlan.mfo.db.Migrations;
import com.atlan.mfo.model.AppUser;
import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.DealStatus;
import com.atlan.mfo.scoring.ScoringEngine;
import com.atlan.mfo.ui.controllers.ChangePasswordController;
import com.atlan.mfo.ui.controllers.LoginController;
import com.atlan.mfo.ui.controllers.MainShellController;
import com.atlan.mfo.ui.util.Formatters;
import com.atlan.mfo.ui.view.DetailView;
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
        com.atlan.mfo.ui.util.Async.shutdown();
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
        // Lecture base (moteur + opportunités) hors thread UI : l'écran ne fige pas.
        com.atlan.mfo.ui.util.Async.run(
                Main::buildPresentationData,
                data -> showPresentationView(user, data),
                com.atlan.mfo.ui.util.ErrorDialog::show);
    }

    /** Données de la vue présentation, calculées hors thread UI. */
    private record PresentationData(ScoringEngine engine, java.util.List<PipelineItem> items,
                                    java.util.Map<Long, FundInvestment> fundById,
                                    java.util.Map<Long, DirectDeal> dealById) {
    }

    private static PresentationData buildPresentationData() {
        ScoringEngine engine = new ScoringConfig().currentEngine();
        java.time.LocalDate today = java.time.LocalDate.now();
        var fundById = new java.util.HashMap<Long, FundInvestment>();
        var dealById = new java.util.HashMap<Long, DirectDeal>();
        var items = new java.util.ArrayList<PipelineItem>();
        for (FundInvestment f : new FundInvestmentDao().findAll()) {
            items.add(PipelineItem.ofFund(f, engine.score(f, today)));
            fundById.put(f.id(), f);
        }
        for (DirectDeal d : new DirectDealDao().findAll()) {
            items.add(PipelineItem.ofDeal(d, engine.score(d, today)));
            dealById.put(d.id(), d);
        }
        return new PresentationData(engine, items, fundById, dealById);
    }

    private void showPresentationView(AppUser user, PresentationData data) {
        Runnable onExitToAnalyst = user.isAnalyst() ? () -> showAnalystShell(user) : null;
        Runnable onFullScreen = () -> stage.setFullScreen(!stage.isFullScreen());
        Runnable onLogout = () -> {
            Session.clear();
            showLogin();
        };
        // L'analyste peut acter les décisions de statut en séance ; le partner reste en lecture seule (§7).
        java.util.function.BiConsumer<PipelineItem, DealStatus> onStatusChange = user.isAnalyst()
                ? (item, status) -> com.atlan.mfo.ui.util.Async.run(
                () -> {
                    if (item.type() == PipelineItem.Type.FUND) {
                        new FundInvestmentDao().updateStatus(item.id(), status, user.id());
                    } else {
                        new DirectDealDao().updateStatus(item.id(), status, user.id());
                    }
                },
                () -> showPresentation(user),   // recharge et reflète le nouveau statut
                com.atlan.mfo.ui.util.ErrorDialog::show)
                : null;
        java.util.function.Consumer<PipelineItem> onOpen =
                item -> openPresentationDetail(item, data.fundById(), data.dealById(), data.engine());
        java.util.function.Function<PipelineItem, String> headline =
                item -> presentationHeadline(item, data.fundById(), data.dealById());

        PresentationView view = new PresentationView(data.items(), onStatusChange, onOpen, headline,
                onExitToAnalyst, onFullScreen, onLogout);
        setScene(view, 1280, 800);
    }

    /** Résumé d'une ligne : millésime le plus récent (fonds) ou métriques clés (deal). */
    private String presentationHeadline(PipelineItem item,
                                        java.util.Map<Long, FundInvestment> fundById,
                                        java.util.Map<Long, DirectDeal> dealById) {
        if (item.type() == PipelineItem.Type.FUND) {
            FundInvestment f = fundById.get(item.id());
            var newest = f.vintages().stream()
                    .max(java.util.Comparator.comparingInt(com.atlan.mfo.model.FundVintage::vintageYear))
                    .orElse(null);
            if (newest == null) {
                return "No vintage reported";
            }
            return "Vintage " + newest.vintageYear()
                    + "  ·  DPI " + Formatters.multiple(newest.dpi())
                    + "  ·  IRR " + Formatters.percent(newest.irr())
                    + "  ·  MOIC " + Formatters.multiple(newest.moic());
        }
        DirectDeal d = dealById.get(item.id());
        return "CAGR " + Formatters.percent(d.cagrPct())
                + "  ·  Exp. IRR " + Formatters.percent(d.expIrrPct())
                + "  ·  Exp. MOIC " + Formatters.multiple(d.expMoic());
    }

    /** Ouvre la fiche complète (lecture seule) d'une opportunité dans une fenêtre modale. */
    private void openPresentationDetail(PipelineItem item,
                                        java.util.Map<Long, FundInvestment> fundById,
                                        java.util.Map<Long, DirectDeal> dealById, ScoringEngine engine) {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setTitle(item.name());
        Runnable close = dialog::close;
        Parent content = item.type() == PipelineItem.Type.FUND
                ? DetailView.ofFundReadOnly(fundById.get(item.id()), engine.score(fundById.get(item.id())), close)
                : DetailView.ofDealReadOnly(dealById.get(item.id()), engine.score(dealById.get(item.id())), close);
        Scene scene = new Scene(content, 1040, 780);
        applyStylesheet(scene);
        dialog.setScene(scene);
        dialog.showAndWait();
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
