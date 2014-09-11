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

import io.bitsquare.bank.BankAccount;
import io.bitsquare.gui.AWTSystemTray;
import io.bitsquare.gui.CodeBehind;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.OverlayController;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.Profiler;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.util.BSFXMLLoader;

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
import javafx.scene.image.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MainViewCB extends CodeBehind<MainPM> {
    private static final Logger log = LoggerFactory.getLogger(MainViewCB.class);

    private NavigationController navigationController;
    private OverlayController overlayController;

    private final ToggleGroup navButtonsGroup = new ToggleGroup();
    private NavigationItem mainNavigationItem;

    private BorderPane baseApplicationContainer;
    private VBox baseOverlayContainer;
    private AnchorPane applicationContainer;
    private AnchorPane contentContainer;
    private HBox leftNavPane, rightNavPane;
    private NetworkSyncPane networkSyncPane;
    private MenuBar menuBar;
    private Label loadingLabel;
    private ToggleButton buyButton, sellButton, homeButton, msgButton, ordersButton, fundsButton, settingsButton,
            accountButton;
    private Pane ordersButtonButtonPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainViewCB(MainPM presentationModel, NavigationController navigationController,
                       OverlayController overlayController) {
        super(presentationModel);

        this.navigationController = navigationController;
        this.overlayController = overlayController;

        // just temp. ugly hack... Popups will be removed
        Popups.setOverlayController(overlayController);

        navigationController.addListener(navigationItems -> {
            if (navigationItems != null) {
                for (int i = 0; i < navigationItems.length; i++) {
                    if (navigationItems[i].getLevel() == 1) {
                        mainNavigationItem = navigationItems[i];
                        break;
                    }
                }
            }

            if (mainNavigationItem == null)
                mainNavigationItem = NavigationItem.HOME;

            loadView(mainNavigationItem);
            selectMainMenuButton(mainNavigationItem);
        });

        overlayController.addListener(new OverlayController.OverlayListener() {
            @Override
            public void onBlurContentRequested() {
                Transitions.blur(baseApplicationContainer);
            }

            @Override
            public void onRemoveBlurContentRequested() {
                Transitions.removeBlur(baseApplicationContainer);
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        Profiler.printMsgWithTime("MainController.initialize");
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

    public void selectMainMenuButton(NavigationItem navigationItem) {
        switch (navigationItem) {
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
                log.error(navigationItem.getFxmlUrl() + " is no main navigation item");
                break;
        }
    }

    @Override
    public Initializable loadView(NavigationItem navigationItem) {
        super.loadView((navigationItem));

        final BSFXMLLoader loader = new BSFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Node view = loader.load();
            contentContainer.getChildren().setAll(view);
            childController = loader.getController();

            if (childController instanceof CodeBehind)
                ((CodeBehind) childController).setParentController(this);

            presentationModel.setSelectedNavigationItem(navigationItem);
            return childController;
        } catch (IOException e) {
            e.getStackTrace();
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

        menuBar = getMenuBar();
        applicationContainer = getApplicationContainer();

        baseApplicationContainer.setTop(menuBar);
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

    private void onMainNavigationAdded() {
        Profiler.printMsgWithTime("MainController.ondMainNavigationAdded");

        presentationModel.takeOfferRequested.addListener((ov, olaValue, newValue) -> {
            final Button alertButton = new Button("", ImageUtil.getIconImageView(ImageUtil.MSG_ALERT));
            alertButton.setId("nav-alert-button");
            alertButton.relocate(36, 19);
            alertButton.setOnAction((e) ->
                    navigationController.navigationTo(NavigationItem.ORDERS, NavigationItem.PENDING_TRADE));
            Tooltip.install(alertButton, new Tooltip("Your offer has been accepted"));
            ordersButtonButtonPane.getChildren().add(alertButton);

            AWTSystemTray.setAlert();
        });

        navigationController.navigationTo(presentationModel.getSelectedNavigationItems());
        onContentAdded();
    }

    private void onContentAdded() {
        Profiler.printMsgWithTime("MainController.onContentAdded");
        Transitions.fadeOutAndRemove(baseOverlayContainer, 1500).setInterpolator(Interpolator.EASE_IN);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

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

        ImageView logo = ImageUtil.getIconImageView(ImageUtil.SPLASH_LOGO);
        logo.setFitWidth(300);
        logo.setFitHeight(300);

        Label subTitle = new Label("The decentralized Bitcoin exchange");
        subTitle.setAlignment(Pos.CENTER);
        subTitle.setId("logo-sub-title-label");

        loadingLabel = new Label();
        loadingLabel.setAlignment(Pos.CENTER);
        loadingLabel.setPadding(new Insets(80, 0, 0, 0));
        loadingLabel.textProperty().bind(presentationModel.splashScreenInfoText);

        vBox.getChildren().addAll(logo, subTitle, loadingLabel);
        return vBox;
    }

    private MenuBar getMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.setUseSystemMenuBar(false);

        Menu fileMenu = new Menu("_File");
        fileMenu.setMnemonicParsing(true);
        MenuItem backupMenuItem = new MenuItem("Backup wallet");
        fileMenu.getItems().addAll(backupMenuItem);

        Menu settingsMenu = new Menu("_Settings");
        settingsMenu.setMnemonicParsing(true);
        MenuItem changePwMenuItem = new MenuItem("Change password");
        settingsMenu.getItems().addAll(changePwMenuItem);

        Menu helpMenu = new Menu("_Help");
        helpMenu.setMnemonicParsing(true);
        MenuItem faqMenuItem = new MenuItem("FAQ");
        MenuItem forumMenuItem = new MenuItem("Forum");
        helpMenu.getItems().addAll(faqMenuItem, forumMenuItem);

        menuBar.getMenus().setAll(fileMenu, settingsMenu, helpMenu);
        return menuBar;
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

        presentationModel.networkSyncComplete.addListener((ov, old, newValue) -> {
            if (newValue)
                networkSyncPane.downloadComplete();
        });

        anchorPane.getChildren().addAll(leftNavPane, rightNavPane, contentContainer, networkSyncPane);
        return anchorPane;
    }

    private void addMainNavigation() {
        homeButton = addNavButton(leftNavPane, "Overview", NavigationItem.HOME);
        buyButton = addNavButton(leftNavPane, "Buy BTC", NavigationItem.BUY);
        sellButton = addNavButton(leftNavPane, "Sell BTC", NavigationItem.SELL);

        ordersButtonButtonPane = new Pane();
        ordersButton = addNavButton(ordersButtonButtonPane, "Orders", NavigationItem.ORDERS);
        leftNavPane.getChildren().add(ordersButtonButtonPane);

        fundsButton = addNavButton(leftNavPane, "Funds", NavigationItem.FUNDS);

        final Pane msgButtonHolder = new Pane();
        msgButton = addNavButton(msgButtonHolder, "Message", NavigationItem.MSG);
        leftNavPane.getChildren().add(msgButtonHolder);

        addBalanceInfo(rightNavPane);

        addBankAccountComboBox(rightNavPane);

        settingsButton = addNavButton(rightNavPane, "Settings", NavigationItem.SETTINGS);
        accountButton = addNavButton(rightNavPane, "Account", NavigationItem.ACCOUNT);

        onMainNavigationAdded();
    }

    private ToggleButton addNavButton(Pane parent, String title, NavigationItem navigationItem) {
        ImageView icon = ImageUtil.getIconImageView(navigationItem.getIcon());
        icon.setFitWidth(32);
        icon.setFitHeight(32);

        final ToggleButton toggleButton = new ToggleButton(title, icon);
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
                Image activeIcon = ImageUtil.getIconImage(navigationItem.getActiveIcon());
                ((ImageView) toggleButton.getGraphic()).setImage(activeIcon);
            }
            else {
                Image activeIcon = ImageUtil.getIconImage(navigationItem.getIcon());
                ((ImageView) toggleButton.getGraphic()).setImage(activeIcon);
            }
        });

        toggleButton.setOnAction(e -> loadView(navigationItem));
        parent.getChildren().add(toggleButton);
        return toggleButton;
    }

    private void addBalanceInfo(Pane parent) {
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
    }

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
