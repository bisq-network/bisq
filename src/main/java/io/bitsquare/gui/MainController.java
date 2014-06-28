package io.bitsquare.gui;

import com.google.inject.Inject;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
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
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.Date;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController implements Initializable, NavigationController
{
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static MainController mainController;


    private final User user;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private final Trading trading;
    private final Storage storage;
    private final String selectedNavigationItemStorageId;
    private final ToggleGroup toggleGroup = new ToggleGroup();

    private ChildController childController;
    private NavigationItem selectedNavigationItem;
    private NetworkSyncPane networkSyncPane;
    private ToggleButton prevToggleButton;
    private Image prevToggleButtonIcon;
    private ToggleButton buyButton, sellButton, homeButton, msgButton, ordersButton, fundsButton, settingsButton;
    private Pane msgButtonHolder, ordersButtonButtonHolder;
    private TextField balanceTextField;

    @FXML
    private Pane contentPane;
    @FXML
    private HBox leftNavPane, rightNavPane;
    @FXML
    private ProgressBar loadingBar;
    @FXML
    private AnchorPane rootPane;
    @FXML
    private Label loadingLabel;


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

        selectedNavigationItemStorageId = this.getClass().getName() + ".selectedNavigationItem";
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
        Platform.runLater(this::init);
    }

    public void init()
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

        Object f = storage.read(selectedNavigationItemStorageId);
        selectedNavigationItem = (NavigationItem) storage.read(selectedNavigationItemStorageId);
        if (selectedNavigationItem == null)
            selectedNavigationItem = NavigationItem.HOME;

        navigateToView(selectedNavigationItem);

        AnchorPane.setBottomAnchor(networkSyncPane, 0.0);
        AnchorPane.setLeftAnchor(networkSyncPane, 0.0);

        messageFacade.addTakeOfferRequestListener(this::showTakeOfferRequest);

        loadingBar.setProgress(-1);
        rootPane.getChildren().removeAll(loadingLabel, loadingBar);

        leftNavPane.setVisible(true);
        rightNavPane.setVisible(true);
        contentPane.setVisible(true);
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

            alertButton.setOnAction((e) -> ordersButton.fire());
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
    public ChildController navigateToView(NavigationItem navigationItem)
    {
        switch (navigationItem)
        {
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
        return null;
    }

    private ChildController loadView(NavigationItem navigationItem)
    {
        if (childController != null)
            childController.cleanup();

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()), Localisation.getResourceBundle());
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
        homeButton = addNavButton(leftNavPane, "Overview", NavigationItem.HOME);

        buyButton = addNavButton(leftNavPane, "Buy BTC", NavigationItem.BUY);

        sellButton = addNavButton(leftNavPane, "Sell BTC", NavigationItem.SELL);

        ordersButtonButtonHolder = new Pane();
        ordersButton = addNavButton(ordersButtonButtonHolder, "Orders", NavigationItem.ORDERS);
        leftNavPane.getChildren().add(ordersButtonButtonHolder);

        fundsButton = addNavButton(leftNavPane, "Funds", NavigationItem.FUNDS);

        msgButtonHolder = new Pane();
        msgButton = addNavButton(msgButtonHolder, "Message", NavigationItem.MSG);
        leftNavPane.getChildren().add(msgButtonHolder);

        addBalanceInfo(rightNavPane);
        addAccountComboBox(rightNavPane);

        settingsButton = addNavButton(rightNavPane, "Settings", NavigationItem.SETTINGS);
    }

    private ToggleButton addNavButton(Pane parent, String title, NavigationItem navigationItem)
    {
        Pane pane = new Pane();
        pane.setPrefSize(50, 50);
        ToggleButton toggleButton = new ToggleButton("", Icons.getIconImageView(navigationItem.getIcon()));
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId("nav-button");
        toggleButton.setPrefSize(50, 50);
        toggleButton.setOnAction(e -> {
            if (prevToggleButton != null)
            {
                ((ImageView) (prevToggleButton.getGraphic())).setImage(prevToggleButtonIcon);
            }
            prevToggleButtonIcon = ((ImageView) (toggleButton.getGraphic())).getImage();
            ((ImageView) (toggleButton.getGraphic())).setImage(Icons.getIconImage(navigationItem.getActiveIcon()));

            childController = loadView(navigationItem);

            if (childController instanceof MarketController)
                ((MarketController) childController).setDirection(navigationItem == NavigationItem.BUY ? Direction.BUY : Direction.SELL);

            storage.write(selectedNavigationItemStorageId, navigationItem);

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

        walletFacade.addBalanceListener(new BalanceListener()
        {
            @Override
            public void onBalanceChanged(BigInteger balance)
            {
                balanceTextField.setText(BtcFormatter.satoshiToString(balance));
            }
        });

        return balanceTextField;
    }

    private void addAccountComboBox(Pane parent)
    {
        if (user.getBankAccounts().size() > 1)
        {
            ComboBox<BankAccount> accountComboBox = new ComboBox(FXCollections.observableArrayList(user.getBankAccounts()));
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

            accountComboBox.valueProperty().addListener((ov, oldValue, newValue) -> user.setCurrentBankAccount(newValue));

        }
    }


}