/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main;

import io.bitsquare.BitsquareException;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.SystemNotification;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.home.HomeView;
import io.bitsquare.gui.main.msg.MsgView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.settings.SettingsView;
import io.bitsquare.gui.main.trade.BuyView;
import io.bitsquare.gui.main.trade.SellView;
import io.bitsquare.gui.util.Transitions;

import javax.inject.Inject;
import javax.inject.Named;

import viewfx.view.FxmlView;
import viewfx.view.View;
import viewfx.view.ViewLoader;
import viewfx.view.support.ActivatableView;
import viewfx.view.support.CachingViewLoader;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;

import static javafx.scene.layout.AnchorPane.*;

@FxmlView
public class MainView extends ActivatableView<StackPane, MainViewModel> {

    public static final String TITLE_KEY = "view.title";

    private final ToggleGroup navButtons = new ToggleGroup();

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final OverlayManager overlayManager;
    private final Transitions transitions;
    private final String title;

    @Inject
    public MainView(MainViewModel model, CachingViewLoader viewLoader, Navigation navigation, OverlayManager
            overlayManager,
                    Transitions transitions, @Named(MainView.TITLE_KEY) String title) {
        super(model);
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.overlayManager = overlayManager;
        this.transitions = transitions;
        this.title = title;

        model.getTradeManager().featureNotImplementedWarningProperty().addListener((ov, oldValue, newValue) -> {
            if (oldValue == null && newValue != null) {
                Popups.openWarningPopup(newValue);
                model.getTradeManager().setFeatureNotImplementedWarning(null);
            }
        });
    }

    @Override
    protected void activate() {
        ToggleButton homeButton = new NavButton(HomeView.class, "Overview") {{
            setDisable(true); // during irc demo
        }};
        ToggleButton buyButton = new NavButton(BuyView.class, "Buy BTC");
        ToggleButton sellButton = new NavButton(SellView.class, "Sell BTC");
        ToggleButton portfolioButton = new NavButton(PortfolioView.class, "Portfolio");
        ToggleButton fundsButton = new NavButton(FundsView.class, "Funds");
        ToggleButton msgButton = new NavButton(MsgView.class, "Messages") {{
            setDisable(true); // during irc demo
        }};
        ToggleButton settingsButton = new NavButton(SettingsView.class, "Settings");
        ToggleButton accountButton = new NavButton(AccountView.class, "Account");
        Pane portfolioButtonHolder = new Pane(portfolioButton);
        Pane bankAccountComboBoxHolder = new Pane();

        HBox leftNavPane = new HBox(
                homeButton, buyButton, sellButton, portfolioButtonHolder, fundsButton, new Pane(msgButton)) {{
            setSpacing(10);
            setLeftAnchor(this, 10d);
            setTopAnchor(this, 0d);
        }};

        HBox rightNavPane = new HBox(bankAccountComboBoxHolder, settingsButton, accountButton) {{
            setSpacing(10);
            setRightAnchor(this, 10d);
            setTopAnchor(this, 0d);
        }};

        AnchorPane contentContainer = new AnchorPane() {{
            setId("content-pane");
            setLeftAnchor(this, 0d);
            setRightAnchor(this, 0d);
            setTopAnchor(this, 60d);
            setBottomAnchor(this, 25d);
        }};

        AnchorPane applicationContainer = new AnchorPane(leftNavPane, rightNavPane, contentContainer) {{
            setId("content-pane");
        }};

        BorderPane baseApplicationContainer = new BorderPane(applicationContainer) {{
            setId("base-content-container");
        }};

        navigation.addListener(viewPath -> {
            if (viewPath.size() != 2 || viewPath.indexOf(MainView.class) != 0)
                return;

            Class<? extends View> viewClass = viewPath.tip();
            View view = viewLoader.load(viewClass);
            contentContainer.getChildren().setAll(view.getRoot());

            navButtons.getToggles().stream()
                    .filter(toggle -> toggle instanceof NavButton)
                    .filter(button -> viewClass == ((NavButton) button).viewClass)
                    .findFirst()
                    .orElseThrow(() -> new BitsquareException("No button matching %s found", viewClass))
                    .setSelected(true);
        });

        configureBlurring(baseApplicationContainer);

        VBox splashScreen = createSplashScreen();

        root.getChildren().addAll(baseApplicationContainer, splashScreen);

        Platform.runLater(
                () -> model.initBackend().subscribe(
                        next -> { },
                        error -> { },
                        () -> Platform.runLater(() -> {
                                    bankAccountComboBoxHolder.getChildren().setAll(createBankAccountComboBox());

                                    applyPendingTradesInfoIcon(model.numPendingTrades.get(), portfolioButtonHolder);
                                    model.numPendingTrades.addListener((ov2, prev2, numPendingTrades) ->
                                            applyPendingTradesInfoIcon((int) numPendingTrades, portfolioButtonHolder));

                                    navigation.navigateToLastOpenView();

                                    transitions.fadeOutAndRemove(splashScreen, 1500);
                                }
                        )
                )
        );
    }

    private VBox createSplashScreen() {
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(10);
        vBox.setId("splash");

        ImageView logo = new ImageView();
        logo.setId("image-splash-logo");

        Label blockchainSyncLabel = new Label();
        blockchainSyncLabel.textProperty().bind(model.blockchainSyncState);
        model.walletServiceErrorMsg.addListener((ov, oldValue, newValue) -> {
            blockchainSyncLabel.setId("splash-error-state-msg");
            Popups.openErrorPopup("Error", "An error occurred at startup. \n\nError message:\n" +
                    newValue);
        });

        ProgressBar blockchainSyncIndicator = new ProgressBar(-1);
        blockchainSyncIndicator.setPrefWidth(120);
        blockchainSyncIndicator.progressProperty().bind(model.blockchainSyncProgress);

        ImageView blockchainSyncIcon = new ImageView();
        blockchainSyncIcon.setVisible(false);
        blockchainSyncIcon.setManaged(false);

        model.blockchainSyncIconId.addListener((ov, oldValue, newValue) -> {
            blockchainSyncIcon.setId(newValue);
            blockchainSyncIcon.setVisible(true);
            blockchainSyncIcon.setManaged(true);

            blockchainSyncIndicator.setVisible(false);
            blockchainSyncIndicator.setManaged(false);
        });

        Label bitcoinNetworkLabel = new Label();
        bitcoinNetworkLabel.setText(model.getBitcoinNetwork().toString());
        bitcoinNetworkLabel.setId("splash-bitcoin-network-label");

        HBox blockchainSyncBox = new HBox();
        blockchainSyncBox.setSpacing(10);
        blockchainSyncBox.setAlignment(Pos.CENTER);
        blockchainSyncBox.setPadding(new Insets(60, 0, 0, 0));
        blockchainSyncBox.setPrefHeight(50);
        blockchainSyncBox.getChildren().addAll(blockchainSyncLabel, blockchainSyncIndicator,
                blockchainSyncIcon, bitcoinNetworkLabel);

        Label bootstrapStateLabel = new Label();
        bootstrapStateLabel.setWrapText(true);
        bootstrapStateLabel.setMaxWidth(500);
        bootstrapStateLabel.setTextAlignment(TextAlignment.CENTER);
        bootstrapStateLabel.textProperty().bind(model.bootstrapStateText);

        ProgressIndicator bootstrapIndicator = new ProgressIndicator();
        bootstrapIndicator.setMaxSize(24, 24);
        bootstrapIndicator.progressProperty().bind(model.bootstrapProgress);

        model.bootstrapFailed.addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                bootstrapStateLabel.setId("splash-error-state-msg");
                bootstrapIndicator.setVisible(false);

                Popups.openErrorPopup("Error", "Cannot connect to P2P network. \n\nError message:\n" +
                        model.bootstrapErrorMsg.get());
            }
        });

        ImageView bootstrapIcon = new ImageView();
        bootstrapIcon.setVisible(false);
        bootstrapIcon.setManaged(false);

        model.bootstrapIconId.addListener((ov, oldValue, newValue) -> {
            bootstrapIcon.setId(newValue);
            bootstrapIcon.setVisible(true);
            bootstrapIcon.setManaged(true);

            bootstrapIndicator.setVisible(false);
            bootstrapIndicator.setManaged(false);
        });

        HBox bootstrapBox = new HBox();
        bootstrapBox.setSpacing(10);
        bootstrapBox.setAlignment(Pos.CENTER);
        bootstrapBox.setPadding(new Insets(10, 0, 0, 0));
        bootstrapBox.setPrefHeight(50);
        bootstrapBox.getChildren().addAll(bootstrapStateLabel, bootstrapIndicator, bootstrapIcon);

        vBox.getChildren().addAll(logo, blockchainSyncBox, bootstrapBox);
        return vBox;
    }

    private VBox createBankAccountComboBox() {
        final ComboBox<BankAccount> comboBox = new ComboBox<>(model.getUser().getBankAccounts());
        comboBox.setLayoutY(12);
        comboBox.setVisibleRowCount(5);
        comboBox.setConverter(model.getBankAccountsConverter());

        comboBox.valueProperty().addListener((ov, oldValue, newValue) ->
                model.getUser().setCurrentBankAccount(newValue));

        comboBox.disableProperty().bind(model.bankAccountsComboBoxDisable);
        comboBox.promptTextProperty().bind(model.bankAccountsComboBoxPrompt);

        model.getUser().currentBankAccountProperty().addListener((ov, oldValue, newValue) ->
                comboBox.getSelectionModel().select(newValue));

        comboBox.getSelectionModel().select(model.getUser().currentBankAccountProperty().get());

        final Label titleLabel = new Label("Bank account");
        titleLabel.setMouseTransparent(true);
        titleLabel.setId("nav-button-label");
        comboBox.widthProperty().addListener((ov, o, n) ->
                titleLabel.setLayoutX(((double) n - titleLabel.getWidth()) / 2));

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 8, 0, 5));
        vBox.setSpacing(2);
        vBox.setAlignment(Pos.CENTER);
        vBox.getChildren().setAll(comboBox, titleLabel);

        return vBox;
    }

    private void applyPendingTradesInfoIcon(int numPendingTrades, Pane targetPane) {
        if (numPendingTrades <= 0) {
            if (targetPane.getChildren().size() > 1) {
                targetPane.getChildren().remove(1);
            }
            return;
        }

        Label numPendingTradesLabel = new Label(String.valueOf(numPendingTrades));
        if (targetPane.getChildren().size() == 1) {
            ImageView icon = new ImageView();
            icon.setLayoutX(0.5);
            icon.setId("image-alert-round");

            numPendingTradesLabel.relocate(5, 1);
            numPendingTradesLabel.setId("nav-alert-label");

            Pane alert = new Pane();
            alert.relocate(30, 9);
            alert.setMouseTransparent(true);
            alert.setEffect(new DropShadow(4, 1, 2, Color.GREY));
            alert.getChildren().addAll(icon, numPendingTradesLabel);
            targetPane.getChildren().add(alert);
        }

        SystemNotification.openInfoNotification(title, "You got a new trade message.");
    }

    private void configureBlurring(Node node) {
        Popups.setOverlayManager(overlayManager);

        overlayManager.addListener(new OverlayManager.OverlayListener() {
            @Override
            public void onBlurContentRequested() {
                transitions.blur(node);
            }

            @Override
            public void onRemoveBlurContentRequested() {
                transitions.removeBlur(node);
            }
        });
    }


    private class NavButton extends ToggleButton {

        private final Class<? extends View> viewClass;

        public NavButton(Class<? extends View> viewClass, String title) {
            super(title, new ImageView() {{
                setId("image-nav-" + viewId(viewClass));
            }});

            this.viewClass = viewClass;

            this.setToggleGroup(navButtons);
            this.setId("nav-button");
            this.setPadding(new Insets(0, -10, -10, -10));
            this.setMinSize(50, 50);
            this.setMaxSize(50, 50);
            this.setContentDisplay(ContentDisplay.TOP);
            this.setGraphicTextGap(0);

            this.selectedProperty().addListener((ov, oldValue, newValue) -> {
                this.setMouseTransparent(newValue);
                this.setMinSize(50, 50);
                this.setMaxSize(50, 50);
                this.setGraphicTextGap(newValue ? -1 : 0);
                if (newValue) {
                    this.getGraphic().setId("image-nav-" + viewId(viewClass) + "-active");
                }
                else {
                    this.getGraphic().setId("image-nav-" + viewId(viewClass));
                }
            });

            this.setOnAction(e -> navigation.navigateTo(MainView.class, viewClass));
        }

    }

    private static String viewId(Class<? extends View> viewClass) {
        String viewName = viewClass.getSimpleName();
        String suffix = "View";
        int suffixIdx = viewName.indexOf(suffix);
        if (suffixIdx != viewName.length() - suffix.length())
            throw new IllegalArgumentException("Cannot get ID for " + viewClass + ": class must end in " + suffix);
        return viewName.substring(0, suffixIdx).toLowerCase();
    }
}