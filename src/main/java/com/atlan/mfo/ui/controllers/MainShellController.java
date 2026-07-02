package com.atlan.mfo.ui.controllers;

import com.atlan.mfo.Main;
import com.atlan.mfo.auth.Session;
import com.atlan.mfo.dao.DirectDealDao;
import com.atlan.mfo.dao.FundInvestmentDao;
import com.atlan.mfo.model.AppUser;
import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.PipelineItem;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.Role;
import com.atlan.mfo.ui.view.DetailView;
import com.atlan.mfo.ui.view.MethodologyView;
import com.atlan.mfo.ui.view.PipelineView;
import com.atlan.mfo.ui.view.SectionView;
import javafx.fxml.FXML;
import javafx.scene.Node;
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
        allItems = new java.util.ArrayList<>();
        funds.forEach(f -> allItems.add(PipelineItem.ofFund(f)));
        deals.forEach(d -> allItems.add(PipelineItem.ofDeal(d)));
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
        return new SectionView(category.label(), items, this::openDetail);
    }

    private Node dealsSection() {
        List<PipelineItem> items = allItems.stream()
                .filter(i -> i.type() == PipelineItem.Type.DEAL)
                .toList();
        return new SectionView(PipelineItem.DEALS_STRATEGY, items, this::openDetail);
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
                    .ifPresent(f -> setContent(DetailView.ofFund(f, onBack)));
        } else {
            deals.stream().filter(d -> d.id() == item.id()).findFirst()
                    .ifPresent(d -> setContent(DetailView.ofDeal(d, onBack)));
        }
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
