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

package io.bitsquare.gui;

import com.google.bitcoin.core.Coin;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.orders.OrdersController;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.Profiler;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.msg.BootstrapListener;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.storage.Persistence;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;
import io.bitsquare.util.AWTSystemTray;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import javax.inject.Inject;

import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the splash screen and the application views. It builds up all the views and initializes the facades.
 * We use a sequence of Platform.runLater cascaded calls to make the startup more smooth, otherwise the rendering is
 * frozen for too long. Pre-loading of views is not implemented yet, and after a quick test it seemed that it does not
 * give much improvements.
 */
public class MainController extends ViewController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static MainController INSTANCE;

    private final User user;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private final TradeManager tradeManager;
    private final Persistence persistence;
    private final ToggleGroup toggleGroup = new ToggleGroup();
    private final ViewBuilder viewBuilder;

    private ToggleButton prevToggleButton;
    private Image prevToggleButtonIcon;
    private ToggleButton buyButton, sellButton, homeButton, msgButton, ordersButton, fundsButton, settingsButton;
    private Pane ordersButtonButtonHolder;
    private boolean messageFacadeInited;
    private boolean walletFacadeInited;
    private VBox accountComboBoxHolder;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static MainController GET_INSTANCE() {
        return INSTANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainController(User user, WalletFacade walletFacade, MessageFacade messageFacade,
                           TradeManager tradeManager, Persistence persistence) {
        this.user = user;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.tradeManager = tradeManager;
        this.persistence = persistence;

        viewBuilder = new ViewBuilder();

        MainController.INSTANCE = this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        Profiler.printMsgWithTime("MainController.initialize");
        Platform.runLater(() -> viewBuilder.buildSplashScreen((BorderPane) root, this));
    }

    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ViewController loadViewAndGetChildController(NavigationItem navigationItem) {
        switch (navigationItem) {
            case HOME:
                homeButton.fire();
                break;
            case FUNDS:
                fundsButton.fire();
                break;
            case MSG:
                msgButton.fire();
                break;
            case ORDERS:
                ordersButton.fire();
                break;
            case SETTINGS:
                settingsButton.fire();
                break;
            case SELL:
                sellButton.fire();
                break;
            case BUY:
                buyButton.fire();
                break;
        }
        return childController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Startup Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onViewInitialized() {
        Profiler.printMsgWithTime("MainController.onViewInitialized");
        Platform.runLater(this::initFacades);
    }

    private void onFacadesInitialised() {
        Profiler.printMsgWithTime("MainController.onFacadesInitialised");
        // never called on regtest
        walletFacade.addDownloadListener(new WalletFacade.DownloadListener() {
            @Override
            public void progress(double percent) {
                viewBuilder.loadingLabel.setText("Synchronise with network...");
                if (viewBuilder.networkSyncPane == null)
                    viewBuilder.setShowNetworkSyncPane();
            }

            @Override
            public void downloadComplete() {
                viewBuilder.loadingLabel.setText("Synchronise with network done.");
                if (viewBuilder.networkSyncPane != null)
                    viewBuilder.networkSyncPane.downloadComplete();
            }
        });

        tradeManager.addTakeOfferRequestListener(this::onTakeOfferRequested);
        Platform.runLater(this::addNavigation);
    }

    private void onNavigationAdded() {
        Profiler.printMsgWithTime("MainController.onNavigationAdded");
        Platform.runLater(this::loadContentView);
    }

    private void onContentViewLoaded() {
        Profiler.printMsgWithTime("MainController.onContentViewLoaded");
        root.setId("main-view");
        Platform.runLater(this::fadeOutSplash);
    }

    private void fadeOutSplash() {
        Profiler.printMsgWithTime("MainController.fadeOutSplash");
        Transitions.blurOutAndRemove(viewBuilder.splashVBox);
        Transitions.fadeIn(viewBuilder.menuBar);
        Transitions.fadeIn(viewBuilder.contentScreen);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO make ordersButton also reacting to jump to pending tab
    private void onTakeOfferRequested(String offerId, PeerAddress sender) {
        final Button alertButton = new Button("", ImageUtil.getIconImageView(ImageUtil.MSG_ALERT));
        alertButton.setId("nav-alert-button");
        alertButton.relocate(36, 19);
        alertButton.setOnAction((e) -> {
            ordersButton.fire();
            OrdersController.GET_INSTANCE().setSelectedTabIndex(1);
        });
        Tooltip.install(alertButton, new Tooltip("Someone accepted your offer"));
        ordersButtonButtonHolder.getChildren().add(alertButton);

        AWTSystemTray.setAlert();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private startup methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initFacades() {
        Profiler.printMsgWithTime("MainController.initFacades");
        messageFacade.init(new BootstrapListener() {
            @Override
            public void onCompleted() {
                messageFacadeInited = true;
                if (walletFacadeInited) onFacadesInitialised();
            }

            @Override
            public void onFailed(Throwable throwable) {
                log.error(throwable.toString());
            }
        });

        walletFacade.initialize(() -> {
            walletFacadeInited = true;
            if (messageFacadeInited) onFacadesInitialised();
        });
    }

    private void addNavigation() {
        Profiler.printMsgWithTime("MainController.addNavigation");
        homeButton = addNavButton(viewBuilder.leftNavPane, "Overview", NavigationItem.HOME);
        buyButton = addNavButton(viewBuilder.leftNavPane, "Buy BTC", NavigationItem.BUY);
        sellButton = addNavButton(viewBuilder.leftNavPane, "Sell BTC", NavigationItem.SELL);

        ordersButtonButtonHolder = new Pane();
        ordersButton = addNavButton(ordersButtonButtonHolder, "Orders", NavigationItem.ORDERS);
        viewBuilder.leftNavPane.getChildren().add(ordersButtonButtonHolder);

        fundsButton = addNavButton(viewBuilder.leftNavPane, "Funds", NavigationItem.FUNDS);

        final Pane msgButtonHolder = new Pane();
        msgButton = addNavButton(msgButtonHolder, "Message", NavigationItem.MSG);
        viewBuilder.leftNavPane.getChildren().add(msgButtonHolder);

        addBalanceInfo(viewBuilder.rightNavPane);

        addAccountComboBox(viewBuilder.rightNavPane);

        user.getBankAccountsSizeProperty().addListener((observableValue, oldValue, newValue) -> {
            if ((int) newValue == 2)
                viewBuilder.rightNavPane.getChildren().add(1, accountComboBoxHolder);
            else if ((int) newValue < 2)
                viewBuilder.rightNavPane.getChildren().remove(accountComboBoxHolder);
        });

        settingsButton = addNavButton(viewBuilder.rightNavPane, "Settings", NavigationItem.SETTINGS);

        Platform.runLater(this::onNavigationAdded);
    }

    private void loadContentView() {
        Profiler.printMsgWithTime("MainController.loadContentView");
        NavigationItem selectedNavigationItem = (NavigationItem) persistence.read(this, "selectedNavigationItem");
        if (selectedNavigationItem == null)
            selectedNavigationItem = NavigationItem.BUY;

        loadViewAndGetChildController(selectedNavigationItem);

        Platform.runLater(this::onContentViewLoaded);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void loadViewFromNavButton(NavigationItem navigationItem) {

       /* if (childController instanceof CachedViewController)
            ((CachedViewController) childController).deactivate();
        else if (childController != null)
            childController.terminate();*/

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Node view = loader.load();
            viewBuilder.contentPane.getChildren().setAll(view);
            childController = loader.getController();
            childController.setParentController(this);
        } catch (IOException e) {
            log.error("Loading view failed. " + navigationItem.getFxmlUrl());
        }
    }

    private ToggleButton addNavButton(Pane parent, String title, NavigationItem navigationItem) {
        final Pane pane = new Pane();
        pane.setPrefSize(50, 50);
        final ToggleButton toggleButton = new ToggleButton("", ImageUtil.getIconImageView(navigationItem.getIcon()));
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId("nav-button");
        toggleButton.setPrefSize(50, 50);
        toggleButton.setOnAction(e -> {
            if (prevToggleButton != null) {
                ((ImageView) (prevToggleButton.getGraphic())).setImage(prevToggleButtonIcon);
            }
            prevToggleButtonIcon = ((ImageView) (toggleButton.getGraphic())).getImage();
            ((ImageView) (toggleButton.getGraphic())).setImage(ImageUtil.getIconImage(navigationItem.getActiveIcon()));

            loadViewFromNavButton(navigationItem);

            persistence.write(this, "selectedNavigationItem", navigationItem);

            prevToggleButton = toggleButton;
        });

        final Label titleLabel = new Label(title);
        titleLabel.setPrefWidth(60);
        titleLabel.setLayoutY(40);
        titleLabel.setId("nav-button-label");
        titleLabel.setMouseTransparent(true);

        pane.getChildren().setAll(toggleButton, titleLabel);
        parent.getChildren().add(pane);

        return toggleButton;
    }

    private void addBalanceInfo(Pane parent) {
        final TextField balanceTextField = new TextField();
        balanceTextField.setEditable(false);
        balanceTextField.setPrefWidth(110);
        balanceTextField.setId("nav-balance-label");
        balanceTextField.setText(BitSquareFormatter.formatCoinWithCode(walletFacade.getWalletBalance()));
        walletFacade.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance) {
                balanceTextField.setText(BitSquareFormatter.formatCoinWithCode(walletFacade.getWalletBalance()));
            }
        });

        final Label titleLabel = new Label("Balance");
        titleLabel.prefWidthProperty().bind(balanceTextField.widthProperty());
        titleLabel.setMouseTransparent(true);
        titleLabel.setId("nav-button-label");

        final VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 0, 0, 0));
        vBox.setSpacing(2);
        vBox.getChildren().setAll(balanceTextField, titleLabel);
        parent.getChildren().add(vBox);
    }

    private void addAccountComboBox(Pane parent) {
        final ComboBox<BankAccount> accountComboBox =
                new ComboBox<>(FXCollections.observableArrayList(user.getBankAccounts()));
        accountComboBox.setId("nav-account-combo-box");
        accountComboBox.setLayoutY(12);
        if (user.getCurrentBankAccount() != null)
            accountComboBox.getSelectionModel().select(user.getCurrentBankAccount());
        accountComboBox.valueProperty().addListener((ov, oldValue, newValue) -> user.setCurrentBankAccount(newValue));
        accountComboBox.setConverter(new StringConverter<BankAccount>() {
            @Override
            public String toString(BankAccount bankAccount) {
                return bankAccount.getAccountTitle();
            }

            @Override
            public BankAccount fromString(String s) {
                return null;
            }
        });

        user.getSelectedBankAccountIndexProperty().addListener(observable ->
                accountComboBox.getSelectionModel().select(user.getCurrentBankAccount()));
        user.getBankAccountsSizeProperty().addListener(observable -> {
            accountComboBox.setItems(FXCollections.observableArrayList(user.getBankAccounts()));
            // need to delay it a bit otherwise it will not be set
            Platform.runLater(() -> accountComboBox.getSelectionModel().select(user.getCurrentBankAccount()));
        });

        final Label titleLabel = new Label("Bank account");
        titleLabel.prefWidthProperty().bind(accountComboBox.widthProperty());
        titleLabel.setMouseTransparent(true);
        titleLabel.setId("nav-button-label");

        accountComboBoxHolder = new VBox();
        accountComboBoxHolder.setPadding(new Insets(12, 0, 0, 0));
        accountComboBoxHolder.setSpacing(2);
        accountComboBoxHolder.getChildren().setAll(accountComboBox, titleLabel);

        if (user.getBankAccounts().size() > 1)
            parent.getChildren().add(accountComboBoxHolder);
    }
}


class ViewBuilder {
    HBox leftNavPane, rightNavPane;
    AnchorPane contentPane;
    NetworkSyncPane networkSyncPane;
    StackPane stackPane;
    AnchorPane contentScreen;
    VBox splashVBox;
    MenuBar menuBar;
    BorderPane root;
    Label loadingLabel;
    boolean showNetworkSyncPane;

    void buildSplashScreen(BorderPane root, MainController controller) {
        Profiler.printMsgWithTime("MainController.ViewBuilder.buildSplashScreen");
        this.root = root;

        stackPane = new StackPane();
        splashVBox = getSplashScreen();
        stackPane.getChildren().add(splashVBox);
        root.setCenter(stackPane);

        menuBar = getMenuBar();
        root.setTop(menuBar);

        Platform.runLater(() -> buildContentView(controller));
    }

    void buildContentView(MainController controller) {
        Profiler.printMsgWithTime("MainController.ViewBuilder.buildContentView");
        contentScreen = getContentScreen();
        stackPane.getChildren().add(contentScreen);

        Platform.runLater(controller::onViewInitialized);
    }

    AnchorPane getContentScreen() {
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setId("content-pane");

        leftNavPane = new HBox();
        leftNavPane.setAlignment(Pos.CENTER);
        leftNavPane.setSpacing(10);
        AnchorPane.setLeftAnchor(leftNavPane, 0d);
        AnchorPane.setTopAnchor(leftNavPane, 0d);

        rightNavPane = new HBox();
        rightNavPane.setAlignment(Pos.CENTER);
        rightNavPane.setSpacing(10);
        AnchorPane.setRightAnchor(rightNavPane, 10d);
        AnchorPane.setTopAnchor(rightNavPane, 0d);

        contentPane = new AnchorPane();
        contentPane.setId("content-pane");
        AnchorPane.setLeftAnchor(contentPane, 0d);
        AnchorPane.setRightAnchor(contentPane, 0d);
        AnchorPane.setTopAnchor(contentPane, 60d);
        AnchorPane.setBottomAnchor(contentPane, 20d);

        anchorPane.getChildren().addAll(leftNavPane, rightNavPane, contentPane);
        anchorPane.setOpacity(0);

        if (showNetworkSyncPane)
            addNetworkSyncPane();

        return anchorPane;
    }

    void setShowNetworkSyncPane() {
        showNetworkSyncPane = true;

        if (contentScreen != null)
            addNetworkSyncPane();
    }

    private void addNetworkSyncPane() {
        networkSyncPane = new NetworkSyncPane();
        networkSyncPane.setSpacing(10);
        networkSyncPane.setPrefHeight(20);
        AnchorPane.setLeftAnchor(networkSyncPane, 0d);
        AnchorPane.setBottomAnchor(networkSyncPane, 5d);

        contentScreen.getChildren().addAll(networkSyncPane);
    }

    VBox getSplashScreen() {
        VBox splashVBox = new VBox();
        splashVBox.setAlignment(Pos.CENTER);
        splashVBox.setSpacing(10);

        ImageView logo = ImageUtil.getIconImageView(ImageUtil.SPLASH_LOGO);
        logo.setFitWidth(270);
        logo.setFitHeight(200);

        ImageView titleLabel = ImageUtil.getIconImageView(ImageUtil.SPLASH_LABEL);
        titleLabel.setFitWidth(300);
        titleLabel.setFitHeight(79);

        Label subTitle = new Label("The P2P Fiat-Bitcoin Exchange");
        subTitle.setAlignment(Pos.CENTER);
        subTitle.setId("logo-sub-title-label");

        loadingLabel = new Label("Initializing...");
        loadingLabel.setAlignment(Pos.CENTER);
        loadingLabel.setPadding(new Insets(80, 0, 0, 0));

        splashVBox.getChildren().addAll(logo, titleLabel, subTitle, loadingLabel);
        return splashVBox;
    }

    MenuBar getMenuBar() {
        MenuBar menuBar = new MenuBar();
        // on mac we could place menu bar in the systems menu
        // menuBar.setUseSystemMenuBar(true);
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
        menuBar.setOpacity(0);
        return menuBar;
    }
}
