package io.bitsquare.gui;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.script.Script;
import com.google.inject.Inject;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.market.MarketController;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.locale.Localisation;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.TradeMessage;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Trading;
import io.bitsquare.user.User;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable, NavigationController
{
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static MainController mainController;

    private User user;
    private WalletFacade walletFacade;
    private MessageFacade messageFacade;
    private Trading trading;
    private ChildController childController;
    private ToggleGroup toggleGroup;
    private ToggleButton prevToggleButton;
    private Image prevToggleButtonIcon;
    private NetworkSyncPane networkSyncPane;
    private ToggleButton buyButton, sellButton, homeButton, msgButton, ordersButton, historyButton, fundsButton, settingsButton;
    private Pane msgButtonHolder, buyButtonHolder, sellButtonHolder, ordersButtonButtonHolder;
    private TextField balanceTextField;
    private Storage storage;
    private String storageId;
    private ToggleButton selectedNavigationItem;

    @FXML
    public Pane contentPane;
    @FXML
    public HBox leftNavPane, rightNavPane;
    @FXML
    public StackPane rootContainer;
    @FXML
    public AnchorPane anchorPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MainController(User user, WalletFacade walletFacade, MessageFacade messageFacade, Trading trading, Storage storage)
    {
        this.user = user;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.trading = trading;
        this.storage = storage;

        MainController.mainController = this;
        storageId = this.getClass().getName() + ".selectedNavigationItem";

    }

    public static MainController getInstance()
    {
        return mainController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        networkSyncPane = new NetworkSyncPane();
        networkSyncPane.setSpacing(10);
        networkSyncPane.setPrefHeight(20);

        messageFacade.init();

        walletFacade.addDownloadListener(new WalletFacade.DownloadListener()
        {
            @Override
            public void progress(double percent, int blocksSoFar, Date date)
            {
                networkSyncPane.setProgress(percent);
            }

            @Override
            public void doneDownload()
            {
                networkSyncPane.doneDownload();
            }
        });

        walletFacade.initWallet();

        buildNavigation();

        selectedNavigationItem = (ToggleButton) storage.read(storageId);
        if (selectedNavigationItem == null)
            selectedNavigationItem = homeButton;

        selectedNavigationItem.fire();
        //homeButton.fire();
        //settingsButton.fire();
        //fundsButton.fire();
        // sellButton.fire();
        // ordersButton.fire();
        // homeButton.fire();
        // msgButton.fire();

        AnchorPane.setBottomAnchor(networkSyncPane, 0.0);
        AnchorPane.setLeftAnchor(networkSyncPane, 0.0);

        messageFacade.addTakeOfferRequestListener((tradingMessage, sender) -> showTakeOfferRequest(tradingMessage, sender));
    }

    private void showTakeOfferRequest(final TradeMessage tradeMessage, PeerAddress sender)
    {
        trading.createOffererPaymentProtocol(tradeMessage, sender);
        try
        {
            ImageView newTradeRequestIcon = Icons.getIconImageView(Icons.MSG_ALERT);
            Button alertButton = new Button("", newTradeRequestIcon);
            alertButton.setId("nav-alert-button");
            alertButton.relocate(36, 19);
            Tooltip.install(alertButton, new Tooltip("Someone accepted your offer"));

            alertButton.setOnAction((e) -> {
                ordersButton.fire();
            });
            ordersButtonButtonHolder.getChildren().add(alertButton);

        } catch (NullPointerException e)
        {
            log.warn("showTakeOfferRequest failed because of a NullPointerException");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ChildController navigateToView(String fxmlView)
    {
        // use the buttons to trigger the change to get the correct button states
        switch (fxmlView)
        {
            case NavigationController.HOME:
                homeButton.fire();
                break;
            case NavigationController.FUNDS:
                fundsButton.fire();
                break;
            case NavigationController.HISTORY:
                historyButton.fire();
                break;
            case NavigationController.MSG:
                msgButton.fire();
                break;
            case NavigationController.ORDERS:
                ordersButton.fire();
                break;
            case NavigationController.SETTINGS:
                settingsButton.fire();
                break;
        }
        return navigateToView(fxmlView, "");
    }

    @Override
    public ChildController navigateToView(String fxmlView, String title)
    {
        if (childController != null)
            childController.cleanup();

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(fxmlView), Localisation.getResourceBundle());
        try
        {
            final Node view = loader.load();
            contentPane.getChildren().setAll(view);
            childController = loader.getController();
            childController.setNavigationController(this);
            return childController;
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void buildNavigation()
    {
        toggleGroup = new ToggleGroup();

        homeButton = addNavButton(leftNavPane, "Overview", Icons.HOME, Icons.HOME_ACTIVE, NavigationController.HOME);

        buyButtonHolder = new Pane();
        buyButton = addNavButton(buyButtonHolder, "Buy BTC", Icons.NAV_BUY, Icons.NAV_BUY_ACTIVE, NavigationController.MARKET, Direction.BUY);
        leftNavPane.getChildren().add(buyButtonHolder);

        sellButtonHolder = new Pane();
        sellButton = addNavButton(sellButtonHolder, "Sell BTC", Icons.NAV_SELL, Icons.NAV_SELL_ACTIVE, NavigationController.MARKET, Direction.SELL);
        leftNavPane.getChildren().add(sellButtonHolder);

        ordersButtonButtonHolder = new Pane();
        ordersButton = addNavButton(ordersButtonButtonHolder, "Orders", Icons.ORDERS, Icons.ORDERS_ACTIVE, NavigationController.ORDERS);
        leftNavPane.getChildren().add(ordersButtonButtonHolder);

        historyButton = addNavButton(leftNavPane, "History", Icons.HISTORY, Icons.HISTORY_ACTIVE, NavigationController.HISTORY);
        fundsButton = addNavButton(leftNavPane, "Funds", Icons.FUNDS, Icons.FUNDS_ACTIVE, NavigationController.FUNDS);

        msgButtonHolder = new Pane();
        msgButton = addNavButton(msgButtonHolder, "Message", Icons.MSG, Icons.MSG_ACTIVE, NavigationController.MSG);
        leftNavPane.getChildren().add(msgButtonHolder);

        addBalanceInfo(rightNavPane);
        addAccountComboBox(rightNavPane);

        settingsButton = addNavButton(rightNavPane, "Settings", Icons.SETTINGS, Icons.SETTINGS_ACTIVE, NavigationController.SETTINGS);
    }

    private ToggleButton addNavButton(Pane parent, String title, String iconId, String iconIdActivated, String navTarget)
    {
        return addNavButton(parent, title, iconId, iconIdActivated, navTarget, null);
    }

    private ToggleButton addNavButton(Pane parent, String title, String iconId, String iconIdActivated, String navTarget, Direction direction)
    {
        Pane pane = new Pane();
        pane.setPrefSize(50, 50);
        ToggleButton toggleButton = new ToggleButton("", Icons.getIconImageView(iconId));
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId("nav-button");
        toggleButton.setPrefSize(50, 50);
        toggleButton.setOnAction(e -> {
            if (prevToggleButton != null)
            {
                ((ImageView) (prevToggleButton.getGraphic())).setImage(prevToggleButtonIcon);
            }
            prevToggleButtonIcon = ((ImageView) (toggleButton.getGraphic())).getImage();
            ((ImageView) (toggleButton.getGraphic())).setImage(Icons.getIconImage(iconIdActivated));

            if (childController instanceof MarketController && direction != null)
            {
                ((MarketController) childController).setDirection(direction);
            }
            else
            {
                childController = navigateToView(navTarget, direction == Direction.BUY ? "Orderbook Buy" : "Orderbook Sell");
                if (childController instanceof MarketController && direction != null)
                {
                    ((MarketController) childController).setDirection(direction);
                }
            }

            prevToggleButton = toggleButton;

        });

        Label titleLabel = new Label(title);
        titleLabel.setPrefWidth(60);
        titleLabel.setLayoutY(40);
        titleLabel.setId("nav-button-label");
        titleLabel.setMouseTransparent(true);

        pane.getChildren().setAll(toggleButton, titleLabel);
        parent.getChildren().add(pane);

        return toggleButton;
    }

    private TextField addBalanceInfo(Pane parent)
    {
        balanceTextField = new TextField();
        balanceTextField.setEditable(false);
        balanceTextField.setPrefWidth(90);
        balanceTextField.setId("nav-balance-label");

        balanceTextField.setText(BtcFormatter.formatSatoshis(walletFacade.getWalletBalance(), false));

        Label balanceCurrencyLabel = new Label("BTC");
        balanceCurrencyLabel.setPadding(new Insets(6, 0, 0, 0));
        HBox hBox = new HBox();
        hBox.setSpacing(2);
        hBox.getChildren().setAll(balanceTextField, balanceCurrencyLabel);

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 0, 0, 0));
        vBox.setSpacing(2);
        Label titleLabel = new Label("Balance");
        titleLabel.setMouseTransparent(true);
        titleLabel.setPrefWidth(90);
        titleLabel.setId("nav-button-label");

        vBox.getChildren().setAll(hBox, titleLabel);
        parent.getChildren().add(vBox);

        balanceTextField.setText(BtcFormatter.satoshiToString(walletFacade.getWalletBalance()));
        walletFacade.getWallet().addEventListener(new WalletEventListener()
        {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
            {
                balanceTextField.setText(BtcFormatter.satoshiToString(newBalance));
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
            {
                balanceTextField.setText(BtcFormatter.satoshiToString(walletFacade.getWallet().getBalance()));
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
            {
                balanceTextField.setText(BtcFormatter.satoshiToString(newBalance));
            }

            @Override
            public void onReorganize(Wallet wallet)
            {
                balanceTextField.setText(BtcFormatter.satoshiToString(walletFacade.getWallet().getBalance()));
            }

            @Override
            public void onWalletChanged(Wallet wallet)
            {
            }

            @Override
            public void onKeysAdded(Wallet wallet, List<ECKey> keys)
            {
            }

            @Override
            public void onScriptsAdded(Wallet wallet, List<Script> scripts)
            {
            }
        });

        return balanceTextField;
    }

    private void addAccountComboBox(Pane parent)
    {
        if (user.getBankAccounts().size() > 1)
        {
            ComboBox accountComboBox = new ComboBox(FXCollections.observableArrayList(user.getBankAccounts()));
            accountComboBox.setLayoutY(12);
            accountComboBox.setValue(user.getCurrentBankAccount());
            accountComboBox.setConverter(new StringConverter<BankAccount>()
            {
                @Override
                public String toString(BankAccount bankAccount)
                {
                    return bankAccount.getAccountTitle();
                }

                @Override
                public BankAccount fromString(String s)
                {
                    return null;
                }
            });

            VBox vBox = new VBox();
            vBox.setPadding(new Insets(12, 0, 0, 0));
            vBox.setSpacing(2);
            Label titleLabel = new Label("Bank account");
            titleLabel.setMouseTransparent(true);
            titleLabel.setPrefWidth(90);
            titleLabel.setId("nav-button-label");

            vBox.getChildren().setAll(accountComboBox, titleLabel);
            parent.getChildren().add(vBox);

            accountComboBox.valueProperty().addListener(new ChangeListener<BankAccount>()
            {
                @Override
                public void changed(ObservableValue ov, BankAccount oldValue, BankAccount newValue)
                {
                    user.setCurrentBankAccount(newValue);
                }
            });

        }
    }


}