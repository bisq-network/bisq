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
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.common.view.CachingViewLoader;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.common.view.InitializableView;
import io.bitsquare.gui.common.view.View;
import io.bitsquare.gui.common.view.ViewLoader;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.SystemNotification;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.home.HomeView;
import io.bitsquare.gui.main.msg.MsgView;
import io.bitsquare.gui.main.offer.BuyOfferView;
import io.bitsquare.gui.main.offer.SellOfferView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.settings.SettingsView;
import io.bitsquare.gui.util.Transitions;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
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
public class MainView extends InitializableView<StackPane, MainViewModel> {

    public static final String TITLE_KEY = "view.title";

    private final ToggleGroup navButtons = new ToggleGroup();

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final OverlayManager overlayManager;
    private final Transitions transitions;
    private final String title;
    private ChangeListener<String> walletServiceErrorMsgListener;
    private ChangeListener<String> blockchainSyncIconIdListener;
    private ChangeListener<String> bootstrapErrorMsgListener;
    private ChangeListener<String> bootstrapIconIdListener;
    private ChangeListener<Number> bootstrapProgressListener;
    private ChangeListener<String> updateIconIdListener;
    private Button restartButton;
    private ProgressIndicator bootstrapIndicator;
    private Label bootstrapStateLabel;
    private ProgressBar blockchainSyncIndicator;
    private Label blockchainSyncLabel;
    private Label updateInfoLabel;
    private List<String> persistedFilesCorrupted;

    @Inject
    public MainView(MainViewModel model, CachingViewLoader viewLoader, Navigation navigation, OverlayManager overlayManager, Transitions transitions,
                    @Named(MainView.TITLE_KEY) String title) {
        super(model);
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.overlayManager = overlayManager;
        this.transitions = transitions;
        this.title = title;
    }

    @Override
    protected void initialize() {
        ToggleButton homeButton = new NavButton(HomeView.class, "Overview") {{
            setDisable(true); // alpha
        }};
        ToggleButton buyButton = new NavButton(BuyOfferView.class, "Buy BTC");
        ToggleButton sellButton = new NavButton(SellOfferView.class, "Sell BTC");
        ToggleButton portfolioButton = new NavButton(PortfolioView.class, "Portfolio");
        ToggleButton fundsButton = new NavButton(FundsView.class, "Funds");
        ToggleButton msgButton = new NavButton(MsgView.class, "Messages") {{
            setDisable(true); // alpha
        }};
        ToggleButton settingsButton = new NavButton(SettingsView.class, "Settings");
        ToggleButton accountButton = new NavButton(AccountView.class, "Account"); /*{{
            setDisable(true); // alpha
        }};*/
        Pane portfolioButtonHolder = new Pane(portfolioButton);
        Pane bankAccountComboBoxHolder = new Pane();

        HBox leftNavPane = new HBox(homeButton, buyButton, sellButton, portfolioButtonHolder, fundsButton, new Pane(msgButton)) {{
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
            setBottomAnchor(this, 10d);
        }};

        AnchorPane applicationContainer = new AnchorPane(leftNavPane, rightNavPane, contentContainer) {{
            setId("content-pane");
        }};

        BorderPane baseApplicationContainer = new BorderPane(applicationContainer) {{
            setId("base-content-container");
        }};
        baseApplicationContainer.setBottom(createFooter());

        setupNotificationIcon(portfolioButtonHolder);

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

        model.showAppScreen.addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                bankAccountComboBoxHolder.getChildren().setAll(createBankAccountComboBox());

                navigation.navigateToPreviousVisitedView();

                if (!persistedFilesCorrupted.isEmpty()) {
                    // show warning that some files has been corrupted
                    Popups.openWarningPopup("Those data base file(s) are not compatible with our current code base." +
                                    "\n" + persistedFilesCorrupted.toString() +
                                    "\n\nWe made a backup of the corrupted file(s) and applied the default values." +
                                    "\n\nThe backup is located at: [data directory]/db/corrupted"
                    );
                }

                transitions.fadeOutAndRemove(splashScreen, 1500, actionEvent -> disposeSplashScreen());
            }
        });

        // Delay a bit to give time for rendering the splash screen
        Platform.runLater(model::initBackend);
    }

    public void setPersistedFilesCorrupted(List<String> persistedFilesCorrupted) {
        this.persistedFilesCorrupted = persistedFilesCorrupted;
    }

    private VBox createSplashScreen() {
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(0);
        vBox.setId("splash");

        ImageView logo = new ImageView();
        logo.setId("image-splash-logo");


        // createBitcoinInfoBox
        blockchainSyncLabel = new Label();
        blockchainSyncLabel.textProperty().bind(model.blockchainSyncInfo);
        walletServiceErrorMsgListener = (ov, oldValue, newValue) -> {
            blockchainSyncLabel.setId("splash-error-state-msg");
            openBTCConnectionErrorPopup(newValue);
        };
        model.walletServiceErrorMsg.addListener(walletServiceErrorMsgListener);

        blockchainSyncIndicator = new ProgressBar(-1);
        blockchainSyncIndicator.setPrefWidth(120);
        blockchainSyncIndicator.progressProperty().bind(model.blockchainSyncProgress);

        ImageView blockchainSyncIcon = new ImageView();
        blockchainSyncIcon.setVisible(false);
        blockchainSyncIcon.setManaged(false);

        blockchainSyncIconIdListener = (ov, oldValue, newValue) -> {
            blockchainSyncIcon.setId(newValue);
            blockchainSyncIcon.setVisible(true);
            blockchainSyncIcon.setManaged(true);

            blockchainSyncIndicator.setVisible(false);
            blockchainSyncIndicator.setManaged(false);
        };
        model.blockchainSyncIconId.addListener(blockchainSyncIconIdListener);

        Label bitcoinNetworkLabel = new Label();
        bitcoinNetworkLabel.setText(model.bitcoinNetworkAsString);
        bitcoinNetworkLabel.setId("splash-bitcoin-network-label");

        HBox blockchainSyncBox = new HBox();
        blockchainSyncBox.setSpacing(10);
        blockchainSyncBox.setAlignment(Pos.CENTER);
        blockchainSyncBox.setPadding(new Insets(40, 0, 0, 0));
        blockchainSyncBox.setPrefHeight(50);
        blockchainSyncBox.getChildren().addAll(blockchainSyncLabel, blockchainSyncIndicator,
                blockchainSyncIcon, bitcoinNetworkLabel);


        // createP2PNetworkBox
        bootstrapStateLabel = new Label();
        bootstrapStateLabel.setWrapText(true);
        bootstrapStateLabel.setMaxWidth(500);
        bootstrapStateLabel.setTextAlignment(TextAlignment.CENTER);
        bootstrapStateLabel.textProperty().bind(model.bootstrapInfo);

        bootstrapIndicator = new ProgressIndicator();
        bootstrapIndicator.setMaxSize(24, 24);
        bootstrapIndicator.progressProperty().bind(model.bootstrapProgress);

        bootstrapErrorMsgListener = (ov, oldValue, newValue) -> {
            bootstrapStateLabel.setId("splash-error-state-msg");
            bootstrapIndicator.setVisible(false);
            openBTCConnectionErrorPopup(model.bootstrapErrorMsg.get());
        };
        model.bootstrapErrorMsg.addListener(bootstrapErrorMsgListener);

        ImageView bootstrapIcon = new ImageView();
        bootstrapIcon.setVisible(false);
        bootstrapIcon.setManaged(false);

        bootstrapIconIdListener = (ov, oldValue, newValue) -> {
            bootstrapIcon.setId(newValue);
            bootstrapIcon.setVisible(true);
            bootstrapIcon.setManaged(true);
        };
        model.bootstrapIconId.addListener(bootstrapIconIdListener);

        bootstrapProgressListener = (ov, oldValue, newValue) -> {
            if ((double) newValue >= 1) {
                bootstrapIndicator.setVisible(false);
                bootstrapIndicator.setManaged(false);
            }
        };
        model.bootstrapProgress.addListener(bootstrapProgressListener);

        HBox bootstrapBox = new HBox();
        bootstrapBox.setSpacing(10);
        bootstrapBox.setAlignment(Pos.CENTER);
        bootstrapBox.setPrefHeight(50);
        bootstrapBox.getChildren().addAll(bootstrapStateLabel, bootstrapIndicator, bootstrapIcon);


        // createUpdateBox
        updateInfoLabel = new Label();
        updateInfoLabel.setTextAlignment(TextAlignment.RIGHT);
        updateInfoLabel.textProperty().bind(model.updateInfo);

        restartButton = new Button("Restart");
        restartButton.setDefaultButton(true);
        restartButton.visibleProperty().bind(model.showRestartButton);
        restartButton.managedProperty().bind(model.showRestartButton);
        restartButton.setOnAction(e -> model.restart());

        ImageView updateIcon = new ImageView();
        updateIcon.setId(model.updateIconId.get());

        updateIconIdListener = (ov, oldValue, newValue) -> {
            updateIcon.setId(newValue);
            updateIcon.setVisible(true);
            updateIcon.setManaged(true);
        };
        model.updateIconId.addListener(updateIconIdListener);

        HBox updateBox = new HBox();
        updateBox.setSpacing(10);
        updateBox.setAlignment(Pos.CENTER);
        updateBox.setPrefHeight(20);
        updateBox.getChildren().addAll(updateInfoLabel, restartButton, updateIcon);

        vBox.getChildren().addAll(logo, blockchainSyncBox, bootstrapBox, updateBox);
        return vBox;
    }

    private void disposeSplashScreen() {
        model.walletServiceErrorMsg.removeListener(walletServiceErrorMsgListener);
        model.blockchainSyncIconId.removeListener(blockchainSyncIconIdListener);
        model.bootstrapErrorMsg.removeListener(bootstrapErrorMsgListener);
        model.bootstrapIconId.removeListener(bootstrapIconIdListener);
        model.bootstrapProgress.removeListener(bootstrapProgressListener);
        model.updateIconId.removeListener(updateIconIdListener);

        blockchainSyncLabel.textProperty().unbind();
        blockchainSyncIndicator.progressProperty().unbind();
        bootstrapStateLabel.textProperty().unbind();
        bootstrapIndicator.progressProperty().unbind();
        updateInfoLabel.textProperty().unbind();
        restartButton.visibleProperty().unbind();
        restartButton.managedProperty().unbind();
    }


    private AnchorPane createFooter() {
        // line
        Separator separator = new Separator();
        separator.setId("footer-pane-line");
        separator.setPrefHeight(1);
        setLeftAnchor(separator, 0d);
        setRightAnchor(separator, 0d);
        setTopAnchor(separator, 0d);

        // BTC
        Label blockchainSyncLabel = new Label();
        blockchainSyncLabel.setId("footer-pane");
        blockchainSyncLabel.textProperty().bind(model.blockchainSyncInfoFooter);

        ProgressBar blockchainSyncIndicator = new ProgressBar(-1);
        blockchainSyncIndicator.setPrefWidth(120);
        blockchainSyncIndicator.setMaxHeight(10);
        blockchainSyncIndicator.progressProperty().bind(model.blockchainSyncProgress);

        Label bitcoinNetworkLabel = new Label();
        bitcoinNetworkLabel.setId("footer-bitcoin-network-label");
        bitcoinNetworkLabel.setText(model.bitcoinNetworkAsString);

        model.walletServiceErrorMsg.addListener((ov, oldValue, newValue) -> {
            bitcoinNetworkLabel.setId("splash-error-state-msg");
            bitcoinNetworkLabel.textProperty().unbind();
            bitcoinNetworkLabel.setText("Not connected");
            openBTCConnectionErrorPopup(newValue);
        });

        model.blockchainSyncProgress.addListener((ov, oldValue, newValue) -> {
            if ((double) newValue >= 1) {
                blockchainSyncIndicator.setVisible(false);
                blockchainSyncIndicator.setManaged(false);
                blockchainSyncLabel.setVisible(false);
                blockchainSyncLabel.setManaged(false);
            }
        });

        HBox blockchainSyncBox = new HBox();
        blockchainSyncBox.setSpacing(10);
        blockchainSyncBox.setAlignment(Pos.CENTER);
        blockchainSyncBox.getChildren().addAll(blockchainSyncLabel, blockchainSyncIndicator, bitcoinNetworkLabel);
        setLeftAnchor(blockchainSyncBox, 10d);
        setBottomAnchor(blockchainSyncBox, 7d);

        // version
        Label versionLabel = new Label();
        versionLabel.setId("footer-pane");
        versionLabel.setTextAlignment(TextAlignment.CENTER);
        versionLabel.setAlignment(Pos.BASELINE_CENTER);
        versionLabel.setText(model.version);
        root.widthProperty().addListener((ov, oldValue, newValue) -> {
            versionLabel.setLayoutX(((double) newValue - versionLabel.getWidth()) / 2);
        });
        setBottomAnchor(versionLabel, 7d);


        // P2P
        Label bootstrapLabel = new Label();
        bootstrapLabel.setId("footer-pane");
        setRightAnchor(bootstrapLabel, 100d);
        setBottomAnchor(bootstrapLabel, 7d);
        bootstrapLabel.textProperty().bind(model.bootstrapInfoFooter);

        ImageView bootstrapIcon = new ImageView();
        setRightAnchor(bootstrapIcon, 60d);
        setBottomAnchor(bootstrapIcon, 9d);
        bootstrapIcon.idProperty().bind(model.bootstrapIconId);

        Label numPeersLabel = new Label();
        numPeersLabel.setId("footer-num-peers");
        setRightAnchor(numPeersLabel, 10d);
        setBottomAnchor(numPeersLabel, 7d);
        numPeersLabel.textProperty().bind(model.numDHTPeers);
        model.bootstrapErrorMsg.addListener((ov, oldValue, newValue) -> {
            bootstrapLabel.setId("splash-error-state-msg");
            bootstrapLabel.textProperty().unbind();
            bootstrapLabel.setText("Not connected");
            Popups.openErrorPopup("Error", "Connecting to the P2P network failed. \n" + newValue
                    + "\nPlease check our internet connection.");
        });

        AnchorPane footerContainer = new AnchorPane(separator, blockchainSyncBox, versionLabel, bootstrapLabel, bootstrapIcon, numPeersLabel) {{
            setId("footer-pane");
            setMinHeight(30);
            setMaxHeight(30);
        }};

        return footerContainer;
    }

    private void setupNotificationIcon(Pane portfolioButtonHolder) {
        Label numPendingTradesLabel = new Label();
        numPendingTradesLabel.textProperty().bind(model.numPendingTradesAsString);
        numPendingTradesLabel.relocate(5, 1);
        numPendingTradesLabel.setId("nav-alert-label");

        ImageView icon = new ImageView();
        icon.setLayoutX(0.5);
        icon.setId("image-alert-round");

        Pane notification = new Pane();
        notification.relocate(30, 9);
        notification.setMouseTransparent(true);
        notification.setEffect(new DropShadow(4, 1, 2, Color.GREY));
        notification.getChildren().addAll(icon, numPendingTradesLabel);
        notification.visibleProperty().bind(model.showPendingTradesNotification);
        portfolioButtonHolder.getChildren().add(notification);

        model.showPendingTradesNotification.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                SystemNotification.openInfoNotification(title, "You got a new trade message.");
        });
    }

    private VBox createBankAccountComboBox() {
        final ComboBox<FiatAccount> comboBox = new ComboBox<>(model.getBankAccounts());
        comboBox.setLayoutY(12);
        comboBox.setVisibleRowCount(5);
        comboBox.setConverter(model.getBankAccountsConverter());
        comboBox.disableProperty().bind(model.bankAccountsComboBoxDisable);
        comboBox.promptTextProperty().bind(model.bankAccountsComboBoxPrompt);

        comboBox.getSelectionModel().selectedItemProperty().addListener((ov, oldValue, newValue) ->
                model.setCurrentBankAccount(newValue));

        model.currentBankAccount.addListener((ov, oldValue, newValue) ->
                comboBox.getSelectionModel().select(newValue));
        comboBox.getSelectionModel().select(model.currentBankAccount.get());

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

        // For alpha
        vBox.setDisable(true);

        return vBox;
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

    private void openBTCConnectionErrorPopup(String errorMsg) {
        Popups.openErrorPopup("Error", "Connecting to the bitcoin network failed. \n" + errorMsg
                + "\nPlease check our internet connection.");
    }
}