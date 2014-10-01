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

import io.bitsquare.BitSquare;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.SystemNotification;
import io.bitsquare.gui.util.Profiler;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.animation.Interpolator;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainViewCB extends ViewCB<MainPM> {
    private static final Logger log = LoggerFactory.getLogger(MainViewCB.class);

    private final Navigation navigation;
    private final OverlayManager overlayManager;

    private final ToggleGroup navButtonsGroup = new ToggleGroup();

    private BorderPane baseApplicationContainer;
    private VBox baseOverlayContainer;
    private AnchorPane contentContainer;
    private HBox leftNavPane, rightNavPane;
    private NetworkSyncPane networkSyncPane;
    private ToggleButton buyButton, sellButton, homeButton, msgButton, ordersButton, fundsButton, settingsButton,
            accountButton;
    private Pane ordersButtonButtonPane;
    private Label numPendingTradesLabel;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainViewCB(MainPM presentationModel, Navigation navigation,
                       OverlayManager overlayManager, TradeManager tradeManager) {
        super(presentationModel);

        this.navigation = navigation;
        this.overlayManager = overlayManager;

        tradeManager.featureNotImplementedWarningProperty().addListener((ov, oldValue, newValue) -> {
            if (oldValue == null && newValue != null) {
                Popups.openWarningPopup(newValue);
                tradeManager.setFeatureNotImplementedWarning(null);
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
        Profiler.printMsgWithTime("MainController.initialize");

        // just temp. ugly hack... Popups will be removed
        Popups.setOverlayManager(overlayManager);

        navigation.addListener(navigationItems -> {
            if (navigationItems != null && navigationItems.length == 2) {
                if (navigationItems[0] == Navigation.Item.MAIN) {
                    loadView(navigationItems[1]);
                    selectMainMenuButton(navigationItems[1]);
                }
            }
        });

        overlayManager.addListener(new OverlayManager.OverlayListener() {
            @Override
            public void onBlurContentRequested() {
                Transitions.blur(baseApplicationContainer);
            }

            @Override
            public void onRemoveBlurContentRequested() {
                Transitions.removeBlur(baseApplicationContainer);
            }
        });

        startup();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        super.loadView((navigationItem));
        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Node view = loader.load();
            contentContainer.getChildren().setAll(view);
            childController = loader.getController();

            if (childController instanceof ViewCB)
                ((ViewCB) childController).setParent(this);

            return childController;
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods: Startup 
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void startup() {
        baseApplicationContainer = getBaseApplicationContainer();
        baseOverlayContainer = getSplashScreen();
        ((StackPane) root).getChildren().addAll(baseApplicationContainer, baseOverlayContainer);

        onBaseContainersCreated();
    }

    private void onBaseContainersCreated() {
        Profiler.printMsgWithTime("MainController.onBaseContainersCreated");

        AnchorPane applicationContainer = getApplicationContainer();
        baseApplicationContainer.setCenter(applicationContainer);

        presentationModel.backendInited.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                onBackendInited();
        });

        presentationModel.initBackend();
    }

    private void onBackendInited() {
        Profiler.printMsgWithTime("MainController.onBackendInited");
        addMainNavigation();
    }

    private void applyPendingTradesInfoIcon(int numPendingTrades) {
        log.debug("numPendingTrades " + numPendingTrades);
        if (numPendingTrades > 0) {
            if (ordersButtonButtonPane.getChildren().size() == 1) {
                ImageView icon = new ImageView();
                icon.setLayoutX(0.5);
                icon.setId("image-alert-round");

                numPendingTradesLabel = new Label(String.valueOf(numPendingTrades));
                numPendingTradesLabel.relocate(5, 1);
                numPendingTradesLabel.setId("nav-alert-label");

                Pane alert = new Pane();
                alert.relocate(30, 9);
                alert.setMouseTransparent(true);
                alert.setEffect(new DropShadow(4, 1, 2, Color.GREY));
                alert.getChildren().addAll(icon, numPendingTradesLabel);
                ordersButtonButtonPane.getChildren().add(alert);
            }
            else {
                numPendingTradesLabel.setText(String.valueOf(numPendingTrades));
            }

            log.trace("openInfoNotification " + BitSquare.getAppName());
            SystemNotification.openInfoNotification(BitSquare.getAppName(), "You got a new trade message.");
        }
        else {
            if (ordersButtonButtonPane.getChildren().size() > 1)
                ordersButtonButtonPane.getChildren().remove(1);
        }
    }

    private void onMainNavigationAdded() {
        Profiler.printMsgWithTime("MainController.ondMainNavigationAdded");

        presentationModel.numPendingTrades.addListener((ov, oldValue, newValue) ->
        {
            //if ((int) newValue > (int) oldValue)
            applyPendingTradesInfoIcon((int) newValue);
        });
        applyPendingTradesInfoIcon(presentationModel.numPendingTrades.get());
        navigation.navigateToLastStoredItem();
        onContentAdded();
    }

    private void onContentAdded() {
        Profiler.printMsgWithTime("MainController.onContentAdded");
        Transitions.fadeOutAndRemove(baseOverlayContainer, 1500).setInterpolator(Interpolator.EASE_IN);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void selectMainMenuButton(Navigation.Item item) {
        switch (item) {
            case HOME:
                homeButton.setSelected(true);
                break;
            case FUNDS:
                fundsButton.setSelected(true);
                break;
            case MSG:
                msgButton.setSelected(true);
                break;
            case ORDERS:
                ordersButton.setSelected(true);
                break;
            case SETTINGS:
                settingsButton.setSelected(true);
                break;
            case SELL:
                sellButton.setSelected(true);
                break;
            case BUY:
                buyButton.setSelected(true);
                break;
            case ACCOUNT:
                accountButton.setSelected(true);
                break;
            default:
                log.error(item.getFxmlUrl() + " is no main navigation item");
                break;
        }
    }

    private BorderPane getBaseApplicationContainer() {
        BorderPane borderPane = new BorderPane();
        borderPane.setId("base-content-container");
        return borderPane;
    }

    private VBox getSplashScreen() {
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(10);
        vBox.setId("splash");

        ImageView logo = new ImageView();
        logo.setId("image-splash-logo");

        Label subTitle = new Label("The decentralized bitcoin exchange");
        subTitle.setAlignment(Pos.CENTER);
        subTitle.setId("logo-sub-title-label");

        Label loadingLabel = new Label();
        loadingLabel.setAlignment(Pos.CENTER);
        loadingLabel.setPadding(new Insets(80, 0, 0, 0));
        loadingLabel.textProperty().bind(presentationModel.splashScreenInfoText);

        vBox.getChildren().addAll(logo, subTitle, loadingLabel);
        return vBox;
    }

    private AnchorPane getApplicationContainer() {
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setId("content-pane");

        leftNavPane = new HBox();
        leftNavPane.setSpacing(10);
        AnchorPane.setLeftAnchor(leftNavPane, 10d);
        AnchorPane.setTopAnchor(leftNavPane, 0d);

        rightNavPane = new HBox();
        rightNavPane.setSpacing(10);
        AnchorPane.setRightAnchor(rightNavPane, 10d);
        AnchorPane.setTopAnchor(rightNavPane, 0d);

        contentContainer = new AnchorPane();
        contentContainer.setId("content-pane");
        AnchorPane.setLeftAnchor(contentContainer, 0d);
        AnchorPane.setRightAnchor(contentContainer, 0d);
        AnchorPane.setTopAnchor(contentContainer, 60d);
        AnchorPane.setBottomAnchor(contentContainer, 25d);

        networkSyncPane = new NetworkSyncPane();
        networkSyncPane.setSpacing(10);
        networkSyncPane.setPrefHeight(20);
        AnchorPane.setLeftAnchor(networkSyncPane, 0d);
        AnchorPane.setBottomAnchor(networkSyncPane, 5d);

        // TODO sometimes it keeps running... deactivate ti for the moment and replace it with the notification pane 
        // from Mike Hearn later
        networkSyncPane.setVisible(false);

        presentationModel.networkSyncComplete.addListener((ov, old, newValue) -> {
            if (newValue)
                networkSyncPane.downloadComplete();
        });

        anchorPane.getChildren().addAll(leftNavPane, rightNavPane, contentContainer, networkSyncPane);
        return anchorPane;
    }

    private void addMainNavigation() {
        homeButton = addNavButton(leftNavPane, "Overview", Navigation.Item.HOME);
        buyButton = addNavButton(leftNavPane, "Buy BTC", Navigation.Item.BUY);
        sellButton = addNavButton(leftNavPane, "Sell BTC", Navigation.Item.SELL);

        ordersButtonButtonPane = new Pane();
        ordersButton = addNavButton(ordersButtonButtonPane, "Orders", Navigation.Item.ORDERS);
        leftNavPane.getChildren().add(ordersButtonButtonPane);

        fundsButton = addNavButton(leftNavPane, "Funds", Navigation.Item.FUNDS);

        final Pane msgButtonHolder = new Pane();
        msgButton = addNavButton(msgButtonHolder, "Messages", Navigation.Item.MSG);
        leftNavPane.getChildren().add(msgButtonHolder);

        //addBalanceInfo(rightNavPane);

        addBankAccountComboBox(rightNavPane);

        settingsButton = addNavButton(rightNavPane, "Preferences", Navigation.Item.SETTINGS);
        accountButton = addNavButton(rightNavPane, "Account", Navigation.Item.ACCOUNT);

        onMainNavigationAdded();
    }

    private ToggleButton addNavButton(Pane parent, String title, Navigation.Item navigationItem) {
        final String url = navigationItem.getFxmlUrl();
        int lastSlash = url.lastIndexOf("/") + 1;
        int end = url.lastIndexOf("View.fxml");
        final String id = url.substring(lastSlash, end).toLowerCase();

        ImageView iconImageView = new ImageView();
        iconImageView.setId("image-nav-" + id);

        final ToggleButton toggleButton = new ToggleButton(title, iconImageView);
        toggleButton.setToggleGroup(navButtonsGroup);
        toggleButton.setId("nav-button");
        toggleButton.setPadding(new Insets(0, -10, -10, -10));
        toggleButton.setMinSize(50, 50);
        toggleButton.setMaxSize(50, 50);
        toggleButton.setContentDisplay(ContentDisplay.TOP);
        toggleButton.setGraphicTextGap(0);

        toggleButton.selectedProperty().addListener((ov, oldValue, newValue) -> {
            toggleButton.setMouseTransparent(newValue);
            toggleButton.setMinSize(50, 50);
            toggleButton.setMaxSize(50, 50);
            toggleButton.setGraphicTextGap(newValue ? -1 : 0);
            if (newValue) {
                toggleButton.getGraphic().setId("image-nav-" + id + "-active");
            }
            else {
                toggleButton.getGraphic().setId("image-nav-" + id);
            }
        });

        toggleButton.setOnAction(e -> navigation.navigationTo(Navigation.Item.MAIN, navigationItem));

        parent.getChildren().add(toggleButton);
        return toggleButton;
    }

    /*private void addBalanceInfo(Pane parent) {
        final TextField balanceTextField = new TextField();
        balanceTextField.setEditable(false);
        balanceTextField.setPrefWidth(110);
        balanceTextField.setId("nav-balance-label");
        balanceTextField.textProperty().bind(presentationModel.balance);


        final Label titleLabel = new Label("Balance");
        titleLabel.setMouseTransparent(true);
        titleLabel.setId("nav-button-label");
        balanceTextField.widthProperty().addListener((ov, o, n) ->
                titleLabel.setLayoutX(((double) n - titleLabel.getWidth()) / 2));

        final VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 5, 0, 0));
        vBox.setSpacing(2);
        vBox.getChildren().setAll(balanceTextField, titleLabel);
        vBox.setAlignment(Pos.CENTER);
        parent.getChildren().add(vBox);
    }*/

    private void addBankAccountComboBox(Pane parent) {
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
        parent.getChildren().add(vBox);
    }
}
