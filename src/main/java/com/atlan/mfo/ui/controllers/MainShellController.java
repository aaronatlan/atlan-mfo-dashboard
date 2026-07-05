package com.atlan.mfo.ui.controllers;

import com.atlan.mfo.Main;
import com.atlan.mfo.auth.Session;
import com.atlan.mfo.dao.DirectDealDao;
import com.atlan.mfo.dao.FundInvestmentDao;
import com.atlan.mfo.dao.StaleDataException;
import com.atlan.mfo.model.AppUser;
import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.ScoreBreakdown;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.Role;
import com.atlan.mfo.scoring.ScoringEngine;
import com.atlan.mfo.ui.view.DealFormView;
import com.atlan.mfo.ui.view.DetailView;
import com.atlan.mfo.ui.view.FundFormView;
import com.atlan.mfo.ui.view.MethodologyView;
import com.atlan.mfo.ui.view.PipelineView;
import com.atlan.mfo.ui.view.SectionView;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Supplier;

/** Coquille applicative : menu latéral, barre supérieure, navigation entre écrans (§6). */
public class MainShellController {

    private final Main app;
    private final AppUser user;

    private final FundInvestmentDao fundDao = new FundInvestmentDao();
    private final DirectDealDao dealDao = new DirectDealDao();
    private final ScoringEngine engine = new ScoringEngine();

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
        loadData();
        buildNav();
    }

    private void loadData() {
        funds = fundDao.findAll();
        deals = dealDao.findAll();
        // Score recalculé en direct par le moteur à l'ouverture (§13.4)
        java.time.LocalDate today = java.time.LocalDate.now();
        allItems = new java.util.ArrayList<>();
        funds.forEach(f -> allItems.add(PipelineItem.ofFund(f, engine.score(f, today).score())));
        deals.forEach(d -> allItems.add(PipelineItem.ofDeal(d, engine.score(d, today).score())));
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

        addSectionLabel("RÉFÉRENCE");
        addNav("Méthodologie", MethodologyView::new, false);
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

    private void show(Supplier<Node> view) {
        currentView = view;
        setContent(view.get());
    }

    private void setContent(Node node) {
        contentPane.getChildren().setAll(node);
    }

    private void openDetail(PipelineItem item) {
        Runnable onBack = () -> setContent(currentView.get());
        if (item.type() == PipelineItem.Type.FUND) {
            funds.stream().filter(f -> f.id() == item.id()).findFirst()
                    .ifPresent(f -> setContent(
                            DetailView.ofFund(f, engine.score(f), onBack, () -> editFund(f))));
        } else {
            deals.stream().filter(d -> d.id() == item.id()).findFirst()
                    .ifPresent(d -> setContent(
                            DetailView.ofDeal(d, engine.score(d), onBack, () -> editDeal(d))));
        }
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

    private void saveFund(FundInvestment fund, ScoreBreakdown breakdown) {
        long uid = Session.currentUser().id();
        try {
            if (fund.id() == 0) {
                fundDao.insert(fund, breakdown, uid);
            } else {
                fundDao.update(fund, breakdown, uid);
            }
        } catch (StaleDataException e) {
            conflict(e.getMessage());
            return;
        }
        reloadAndReturn();
    }

    private void saveDeal(DirectDeal deal, ScoreBreakdown breakdown) {
        long uid = Session.currentUser().id();
        try {
            if (deal.id() == 0) {
                dealDao.insert(deal, breakdown, uid);
            } else {
                dealDao.update(deal, breakdown, uid);
            }
        } catch (StaleDataException e) {
            conflict(e.getMessage());
            return;
        }
        reloadAndReturn();
    }

    /** Recharge les données puis réaffiche l'écran courant (avec les scores à jour). */
    private void reloadAndReturn() {
        loadData();
        setContent(currentView.get());
    }

    private void backToList() {
        setContent(currentView.get());
    }

    private void conflict(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Conflit d'édition");
        alert.setHeaderText("Enregistrement refusé");
        alert.setContentText(message + "\n\nLes données à jour vont être rechargées ; réappliquez vos modifications.");
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
        return role == Role.PARTNER ? "Partner" : "Analyste";
    }
}
