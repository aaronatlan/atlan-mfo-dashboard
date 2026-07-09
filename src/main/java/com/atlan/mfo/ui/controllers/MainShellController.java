package com.atlan.mfo.ui.controllers;

import com.atlan.mfo.Main;
import com.atlan.mfo.auth.Session;
import com.atlan.mfo.dao.DirectDealDao;
import com.atlan.mfo.dao.FundInvestmentDao;
import com.atlan.mfo.dao.OutcomeDao;
import com.atlan.mfo.dao.StaleDataException;
import com.atlan.mfo.model.AppUser;
import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.Outcome;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.dao.ScoringConfig;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.Role;
import com.atlan.mfo.scoring.ScoringEngine;
import com.atlan.mfo.ui.util.Async;
import com.atlan.mfo.ui.util.ErrorDialog;
import com.atlan.mfo.ui.view.CalibrationView;
import com.atlan.mfo.ui.view.ComparisonView;
import com.atlan.mfo.ui.view.DealFormView;
import com.atlan.mfo.ui.view.DetailView;
import com.atlan.mfo.ui.view.OutcomeView;
import com.atlan.mfo.ui.view.FundFormView;
import com.atlan.mfo.ui.view.MethodologyView;
import com.atlan.mfo.ui.view.PipelineView;
import com.atlan.mfo.ui.view.SectionView;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Coquille applicative : menu latéral, barre supérieure, navigation entre écrans (§6). */
public class MainShellController {

    private final Main app;
    private final AppUser user;

    private final FundInvestmentDao fundDao = new FundInvestmentDao();
    private final DirectDealDao dealDao = new DirectDealDao();
    private final OutcomeDao outcomeDao = new OutcomeDao();
    private final ScoringConfig scoringConfig = new ScoringConfig();
    private ScoringEngine engine = scoringConfig.currentEngine();

    private List<FundInvestment> funds;
    private List<DirectDeal> deals;
    private List<PipelineItem> allItems;

    private final ToggleGroup navGroup = new ToggleGroup();
    private Supplier<Node> currentView;

    @FXML private VBox sidebar;
    @FXML private StackPane contentPane;
    @FXML private Label userLabel;

    public MainShellController(Main app, AppUser user) {
        this.app = app;
        this.user = user;
    }

    @FXML
    private void initialize() {
        userLabel.setText(user.fullName() + "  ·  " + roleLabel(user.role()));
        // Chargement initial en tâche de fond, puis construction du menu (§13.4).
        reload(this::buildNav);
    }

    /** Instantané des données chargées (calculé hors thread UI, appliqué sur le thread UI). */
    private record Snapshot(List<FundInvestment> funds, List<DirectDeal> deals, List<PipelineItem> items) {
    }

    /** Lit fonds + deals et recalcule les scores en direct par le moteur (§13.4). */
    private Snapshot fetch() {
        List<FundInvestment> f = fundDao.findAll();
        List<DirectDeal> d = dealDao.findAll();
        java.time.LocalDate today = java.time.LocalDate.now();
        List<PipelineItem> items = new ArrayList<>();
        f.forEach(x -> items.add(PipelineItem.ofFund(x, engine.score(x, today))));
        d.forEach(x -> items.add(PipelineItem.ofDeal(x, engine.score(x, today))));
        return new Snapshot(f, d, items);
    }

    private void apply(Snapshot s) {
        funds = s.funds();
        deals = s.deals();
        allItems = s.items();
    }

    /** Recharge les données en tâche de fond, puis exécute {@code after} sur le thread UI. */
    private void reload(Runnable after) {
        setBusy(true);
        Async.run(this::fetch,
                s -> {
                    apply(s);
                    setBusy(false);
                    if (after != null) {
                        after.run();
                    }
                },
                ex -> {
                    setBusy(false);
                    ErrorDialog.show(ex);
                });
    }

    /** Curseur d'attente pendant un accès base (indicateur léger, universel). */
    private void setBusy(boolean busy) {
        Scene sc = contentPane.getScene();
        if (sc != null) {
            sc.setCursor(busy ? Cursor.WAIT : Cursor.DEFAULT);
        }
    }

    private void buildNav() {
        addSectionLabel("NAVIGATION");
        addNav("Pipeline summary",
                () -> new PipelineView(allItems, this::openDetail), true);
        addNav(Category.BUYOUT_GROWTH_VC.label(),
                () -> section(Category.BUYOUT_GROWTH_VC), false);
        addNav(Category.SECONDARIES.label(),
                () -> section(Category.SECONDARIES), false);
        addNav(Category.PRIVATE_CREDIT.label(),
                () -> section(Category.PRIVATE_CREDIT), false);
        addNav(PipelineItem.DEALS_STRATEGY,
                this::dealsSection, false);
        addNav("Decisions", this::decisionsSection, false);
        addNav("Compare", () -> new ComparisonView(allItems, this::scoreOf), false);

        addSectionLabel("REFERENCE");
        addNav("Calibration", () -> new CalibrationView(outcomeDao.findAll()), false);
        addNav("Methodology",
                () -> new MethodologyView(scoringConfig.currentProfile(), this::saveMethodology), false);

        // Signature discrète, ton sur ton : invisible à l'œil, sélectionnable à la souris.
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        TextField signature = new TextField("Aaron Atlan");
        signature.setEditable(false);
        signature.setFocusTraversable(false);
        signature.getStyleClass().add("signature");
        sidebar.getChildren().addAll(spacer, signature);
    }

    /** Saves the edited methodology then recalculates all scores. */
    private void saveMethodology(java.util.Map<String, Double> params) {
        setBusy(true);
        Async.run(
                () -> {
                    scoringConfig.save(params);
                    engine = scoringConfig.currentEngine();   // moteur reconstruit avec les nouveaux poids
                    return fetch();                           // scores recalculés avec le nouveau moteur
                },
                s -> {
                    apply(s);
                    setBusy(false);
                    setContent(currentView.get());
                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setTitle("Methodology");
                    done.setHeaderText("Methodology saved");
                    done.setContentText("All opportunity scores have been recalculated.");
                    done.setGraphic(null);
                    done.getDialogPane().setGraphic(null);
                    var css = getClass().getResource("/css/atlan-dark.css");
                    if (css != null) {
                        done.getDialogPane().getStylesheets().add(css.toExternalForm());
                    }
                    done.showAndWait();
                },
                ex -> {
                    setBusy(false);
                    ErrorDialog.show(ex);
                });
    }

    private void addSectionLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("sidebar-section");
        sidebar.getChildren().add(l);
    }

    private void addNav(String label, Supplier<Node> view, boolean selected) {
        ToggleButton btn = new ToggleButton(label);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setToggleGroup(navGroup);
        btn.setOnAction(e -> {
            btn.setSelected(true);   // empêche la désélection par re-clic
            show(view);
        });
        sidebar.getChildren().add(btn);
        if (selected) {
            btn.setSelected(true);
            show(view);
        }
    }

    private Node section(Category category) {
        List<PipelineItem> items = allItems.stream()
                .filter(i -> i.category() == category)
                .toList();
        return new SectionView(category.label(), items, this::openDetail, () -> newFund(category));
    }

    private Node dealsSection() {
        List<PipelineItem> items = allItems.stream()
                .filter(i -> i.type() == PipelineItem.Type.DEAL)
                .toList();
        return new SectionView(PipelineItem.DEALS_STRATEGY, items, this::openDetail, this::newDeal);
    }

    /** Journal des décisions : opportunités approuvées ou déclinées, conservées (§6.1). */
    private Node decisionsSection() {
        List<PipelineItem> items = allItems.stream()
                .filter(PipelineItem::isDecided)
                .toList();
        return new SectionView("Decisions", items, this::openDetail);
    }

    /** Recalcule en direct le score d'une opportunité (pour la vue comparaison). */
    private ScoreBreakdown scoreOf(PipelineItem item) {
        if (item.type() == PipelineItem.Type.FUND) {
            return funds.stream().filter(f -> f.id() == item.id()).findFirst()
                    .map(f -> engine.score(f)).orElse(null);
        }
        return deals.stream().filter(d -> d.id() == item.id()).findFirst()
                .map(d -> engine.score(d)).orElse(null);
    }

    private void show(Supplier<Node> view) {
        currentView = view;
        setContent(view.get());
    }

    private void setContent(Node node) {
        contentPane.getChildren().setAll(node);
    }

    private void openDetail(PipelineItem item) {
        Runnable onBack = () -> setContent(currentView.get());
        // Le bouton « Outcome » (saisie du réalisé) n'apparaît que pour les décidés.
        Runnable onOutcome = item.isDecided() ? () -> openOutcome(item) : null;
        if (item.type() == PipelineItem.Type.FUND) {
            funds.stream().filter(f -> f.id() == item.id()).findFirst()
                    .ifPresent(f -> setContent(DetailView.ofFund(
                            f, engine.score(f), onBack, () -> editFund(f), () -> deleteFund(f), onOutcome)));
        } else {
            deals.stream().filter(d -> d.id() == item.id()).findFirst()
                    .ifPresent(d -> setContent(DetailView.ofDeal(
                            d, engine.score(d), onBack, () -> editDeal(d), () -> deleteDeal(d), onOutcome)));
        }
    }

    /* ---- Boucle prédit → réalisé (calibration) ---- */

    /** Ouvre l'éditeur d'outcome : charge l'existant + fige le prédit courant. */
    private void openOutcome(PipelineItem item) {
        setBusy(true);
        Async.run(
                () -> outcomeDao.find(item.type().name(), item.id()),
                existing -> {
                    setBusy(false);
                    Double expIrr = null;
                    Double expMoic = null;
                    if (item.type() == PipelineItem.Type.DEAL) {
                        DirectDeal d = deals.stream().filter(x -> x.id() == item.id())
                                .findFirst().orElse(null);
                        if (d != null) {
                            expIrr = d.expIrrPct();
                            expMoic = d.expMoic();
                        }
                    }
                    setContent(new OutcomeView(item, item.score(), expIrr, expMoic, existing,
                            this::saveOutcome, () -> openDetail(item)));
                },
                ex -> {
                    setBusy(false);
                    ErrorDialog.show(ex);
                });
    }

    private void saveOutcome(Outcome outcome) {
        long uid = Session.currentUser().id();
        setBusy(true);
        Async.run(
                () -> outcomeDao.upsert(outcome, uid),
                () -> {
                    setBusy(false);
                    allItems.stream()
                            .filter(i -> i.id() == outcome.opportunityId()
                                    && i.type().name().equals(outcome.kind()))
                            .findFirst()
                            .ifPresentOrElse(this::openDetail, () -> setContent(currentView.get()));
                },
                ex -> {
                    setBusy(false);
                    ErrorDialog.show(ex);
                });
    }

    /* ---- Saisie / édition (Phase 3) ---- */

    private void newFund(Category category) {
        setContent(new FundFormView(null, category, engine, this::saveFund, this::backToList));
    }

    private void editFund(FundInvestment fund) {
        setContent(new FundFormView(fund, fund.category(), engine, this::saveFund, this::backToList));
    }

    private void newDeal() {
        setContent(new DealFormView(null, engine, this::saveDeal, this::backToList));
    }

    private void editDeal(DirectDeal deal) {
        setContent(new DealFormView(deal, engine, this::saveDeal, this::backToList));
    }

    private void deleteFund(FundInvestment fund) {
        if (confirmDelete("fund \"" + fund.name() + "\"")) {
            writeThenReturn(() -> fundDao.delete(fund.id()));
        }
    }

    private void deleteDeal(DirectDeal deal) {
        if (confirmDelete("deal \"" + deal.name() + "\"")) {
            writeThenReturn(() -> dealDao.delete(deal.id()));
        }
    }

    private void saveFund(FundInvestment fund, ScoreBreakdown breakdown) {
        long uid = Session.currentUser().id();
        writeThenReturn(() -> {
            if (fund.id() == 0) {
                fundDao.insert(fund, breakdown, uid);
            } else {
                fundDao.update(fund, breakdown, uid);
            }
        });
    }

    private void saveDeal(DirectDeal deal, ScoreBreakdown breakdown) {
        long uid = Session.currentUser().id();
        writeThenReturn(() -> {
            if (deal.id() == 0) {
                dealDao.insert(deal, breakdown, uid);
            } else {
                dealDao.update(deal, breakdown, uid);
            }
        });
    }

    /**
     * Exécute une écriture base (hors thread UI), puis recharge et réaffiche l'écran
     * courant. Un conflit de verrou optimiste (§13.2) déclenche le dialogue dédié ;
     * toute autre erreur (base injoignable…) le dialogue d'erreur standard.
     */
    private void writeThenReturn(Runnable write) {
        setBusy(true);
        Async.run(
                () -> {
                    write.run();
                    return fetch();
                },
                s -> {
                    apply(s);
                    setBusy(false);
                    setContent(currentView.get());
                },
                ex -> {
                    setBusy(false);
                    if (ex instanceof StaleDataException) {
                        conflict(ex.getMessage());
                    } else {
                        ErrorDialog.show(ex);
                    }
                });
    }

    /** Recharge les données puis réaffiche l'écran courant (avec les scores à jour). */
    private void reloadAndReturn() {
        reload(() -> setContent(currentView.get()));
    }

    private void backToList() {
        setContent(currentView.get());
    }

    /** Confirmation dialog before a permanent deletion. */
    private boolean confirmDelete(String what) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm deletion");
        alert.setHeaderText("Delete " + what + "?");
        alert.setContentText("This action is permanent and cannot be undone.");
        ButtonType delete = new ButtonType("Delete", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(cancel, delete);
        alert.setGraphic(null);                       // retire l'icône « ? » système
        alert.getDialogPane().setGraphic(null);
        var css = getClass().getResource("/css/atlan-dark.css");
        if (css != null) {
            alert.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        return alert.showAndWait().orElse(cancel) == delete;
    }

    private void conflict(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Edit conflict");
        alert.setHeaderText("Save rejected");
        alert.setContentText(message + "\n\nThe latest data will be reloaded; please reapply your changes.");
        alert.setGraphic(null);
        alert.getDialogPane().setGraphic(null);
        // Les dialogues ont leur propre scène : sans cela, le thème ne s'applique pas.
        var css = getClass().getResource("/css/atlan-dark.css");
        if (css != null) {
            alert.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        alert.showAndWait();
        reloadAndReturn();
    }

    @FXML
    private void onPresentation() {
        app.showPresentation(user);
    }

    @FXML
    private void onLogout() {
        Session.clear();
        app.showLogin();
    }

    private static String roleLabel(Role role) {
        return role == Role.PARTNER ? "Partner" : "Analyst";
    }
}
