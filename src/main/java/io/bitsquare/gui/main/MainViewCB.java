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
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.SystemNotification;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.trade.TradeManager;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;
import javax.inject.Named;

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

import static io.bitsquare.gui.Navigation.Item.*;
import static javafx.scene.layout.AnchorPane.*;

public class MainViewCB extends ViewCB<MainPM> {

    private final ToggleGroup navButtons = new ToggleGroup();

    private final AnchorPane contentContainer = new AnchorPane() {{
        setId("content-pane");
        setLeftAnchor(this, 0d);
        setRightAnchor(this, 0d);
        setTopAnchor(this, 60d);
        setBottomAnchor(this, 25d);
    }};

    private final Navigation navigation;
    private final OverlayManager overlayManager;
    private final Transitions transitions;
    private final BitcoinNetwork bitcoinNetwork;
    private final String title;

    @Inject
    public MainViewCB(MainPM presentationModel, Navigation navigation, OverlayManager overlayManager,
                      TradeManager tradeManager, Transitions transitions, BitcoinNetwork bitcoinNetwork,
                      @Named(TITLE_KEY) String title) {
        super(presentationModel);

        this.navigation = navigation;
        this.overlayManager = overlayManager;
        this.transitions = transitions;
        this.bitcoinNetwork = bitcoinNetwork;
        this.title = title;

        tradeManager.featureNotImplementedWarningProperty().addListener((ov, oldValue, newValue) -> {
            if (oldValue == null && newValue != null) {
                Popups.openWarningPopup(newValue);
                tradeManager.setFeatureNotImplementedWarning(null);
            }
        });
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        ToggleButton homeButton = new NavButton(HOME) {{ setDisable(true); }};
        ToggleButton buyButton = new NavButton(BUY);
        ToggleButton sellButton = new NavButton(SELL);
        ToggleButton portfolioButton = new NavButton(PORTFOLIO);
        ToggleButton fundsButton = new NavButton(FUNDS);
        ToggleButton msgButton = new NavButton(MSG) {{ setDisable(true); }};
        ToggleButton settingsButton = new NavButton(SETTINGS);
        ToggleButton accountButton = new NavButton(ACCOUNT);
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

        AnchorPane applicationContainer = new AnchorPane(leftNavPane, rightNavPane, contentContainer) {{
            setId("content-pane");
        }};

        BorderPane baseApplicationContainer = new BorderPane(applicationContainer) {{
            setId("base-content-container");
        }};

        navigation.addListener(navigationItems -> {
            if (isRequestToChangeNavigation(navigationItems))
                loadSelectedNavigation(navigationItems[1]);
        });

        configureBlurring(baseApplicationContainer);

        VBox splashScreen = createSplashScreen();

        ((Pane) root).getChildren().addAll(baseApplicationContainer, splashScreen);

        presentationModel.backendReady.addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                bankAccountComboBoxHolder.getChildren().setAll(createBankAccountComboBox());

                applyPendingTradesInfoIcon(presentationModel.numPendingTrades.get(), portfolioButtonHolder);
                presentationModel.numPendingTrades.addListener((ov1, oldValue1, newValue1) ->
                        applyPendingTradesInfoIcon((int) newValue1, portfolioButtonHolder));

                navigation.navigateToLastStoredItem();
                transitions.fadeOutAndRemove(splashScreen, 1500);
            }
        });

        Platform.runLater(presentationModel::initBackend);
    }

    private void loadSelectedNavigation(Navigation.Item selected) {
        ViewLoader loader = new ViewLoader(selected);
        contentContainer.getChildren().setAll(loader.<Node>load());
        childController = loader.getController();
        if (childController != null)
            childController.setParent(this);

        navButtons.getToggles().stream()
                .filter(toggle -> toggle instanceof ToggleButton)
                .filter(button -> selected.getDisplayName().equals(((ToggleButton) button).getText()))
                .findFirst()
                .orElseThrow(() -> new BitsquareException("No button matching %s found", selected.getDisplayName()))
                .setSelected(true);
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

    private VBox createSplashScreen() {
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(10);
        vBox.setId("splash");

        ImageView logo = new ImageView();
        logo.setId("image-splash-logo");

        Label blockchainSyncLabel = new Label();
        blockchainSyncLabel.textProperty().bind(presentationModel.blockchainSyncState);
        presentationModel.walletServiceErrorMsg.addListener((ov, oldValue, newValue) -> {
            blockchainSyncLabel.setId("splash-error-state-msg");
            Popups.openErrorPopup("Error", "An error occurred at startup. \n\nError message:\n" +
                    newValue);
        });

        ProgressBar blockchainSyncIndicator = new ProgressBar(-1);
        blockchainSyncIndicator.setPrefWidth(120);
        blockchainSyncIndicator.progressProperty().bind(presentationModel.blockchainSyncProgress);

        ImageView blockchainSyncIcon = new ImageView();
        blockchainSyncIcon.setVisible(false);
        blockchainSyncIcon.setManaged(false);

        presentationModel.blockchainSyncIconId.addListener((ov, oldValue, newValue) -> {
            blockchainSyncIcon.setId(newValue);
            blockchainSyncIcon.setVisible(true);
            blockchainSyncIcon.setManaged(true);

            blockchainSyncIndicator.setVisible(false);
            blockchainSyncIndicator.setManaged(false);
        });

        Label bitcoinNetworkLabel = new Label();
        bitcoinNetworkLabel.setText(bitcoinNetwork.toString());
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
        bootstrapStateLabel.textProperty().bind(presentationModel.bootstrapState);

        ProgressIndicator bootstrapIndicator = new ProgressIndicator();
        bootstrapIndicator.setMaxSize(24, 24);
        bootstrapIndicator.progressProperty().bind(presentationModel.bootstrapProgress);

        presentationModel.bootstrapFailed.addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                bootstrapStateLabel.setId("splash-error-state-msg");
                bootstrapIndicator.setVisible(false);

                Popups.openErrorPopup("Error", "Cannot connect to P2P network. \n\nError message:\n" +
                        presentationModel.bootstrapErrorMsg.get());
            }
        });

        ImageView bootstrapIcon = new ImageView();
        bootstrapIcon.setVisible(false);
        bootstrapIcon.setManaged(false);

        presentationModel.bootstrapIconId.addListener((ov, oldValue, newValue) -> {
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
        final ComboBox<BankAccount> comboBox = new ComboBox<>(presentationModel.getBankAccounts());
        comboBox.setLayoutY(12);
        comboBox.setVisibleRowCount(5);
        comboBox.setConverter(presentationModel.getBankAccountsConverter());

        comboBox.valueProperty().addListener((ov, oldValue, newValue) ->
                presentationModel.setCurrentBankAccount(newValue));

        comboBox.disableProperty().bind(presentationModel.bankAccountsComboBoxDisable);
        comboBox.promptTextProperty().bind(presentationModel.bankAccountsComboBoxPrompt);

        presentationModel.currentBankAccountProperty().addListener((ov, oldValue, newValue) ->
                comboBox.getSelectionModel().select(newValue));

        comboBox.getSelectionModel().select(presentationModel.currentBankAccountProperty().get());

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

    private boolean isRequestToChangeNavigation(Navigation.Item[] navigationItems) {
        return navigationItems != null && navigationItems.length == 2 && navigationItems[0] == Navigation.Item.MAIN;
    }


    private class NavButton extends ToggleButton {

        public NavButton(Navigation.Item item) {
            super(item.getDisplayName(), new ImageView() {{
                setId("image-nav-" + item.getId());
            }});

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
                    this.getGraphic().setId("image-nav-" + item.getId() + "-active");
                }
                else {
                    this.getGraphic().setId("image-nav-" + item.getId());
                }
            });

            this.setOnAction(e -> navigation.navigationTo(Navigation.Item.MAIN, item));
        }
    }
}