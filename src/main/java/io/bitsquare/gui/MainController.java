package io.bitsquare.gui;

import com.google.bitcoin.core.Coin;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.market.MarketController;
import io.bitsquare.gui.orders.OrdersController;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.locale.Localisation;
import io.bitsquare.msg.BootstrapListener;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.storage.Persistence;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Trading;
import io.bitsquare.user.User;
import io.bitsquare.util.AWTSystemTray;
import java.io.IOException;
import java.net.URL;
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
import javax.inject.Inject;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController implements Initializable, NavigationController
{
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static MainController INSTANCE;

    private final User user;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private final Trading trading;
    private final Persistence persistence;
    private final ToggleGroup toggleGroup = new ToggleGroup();


    private ChildController childController;
    private ToggleButton prevToggleButton;
    private Image prevToggleButtonIcon;
    private ToggleButton buyButton, sellButton, homeButton, msgButton, ordersButton, fundsButton, settingsButton;
    private Pane ordersButtonButtonHolder;
    private boolean messageFacadeInited;
    private boolean walletFacadeInited;

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
    @FXML
    private NetworkSyncPane networkSyncPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainController(User user, WalletFacade walletFacade, MessageFacade messageFacade, Trading trading, Persistence persistence)
    {
        this.user = user;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.trading = trading;
        this.persistence = persistence;

        MainController.INSTANCE = this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static MainController GET_INSTANCE()
    {
        return INSTANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        Platform.runLater(this::init);
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
        return childController;
    }


    private ChildController loadView(NavigationItem navigationItem)
    {
        if (childController != null)
        {
            childController.cleanup();
        }

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()), Localisation.getResourceBundle());
        try
        {
            final Node view = loader.load();
            contentPane.getChildren().setAll(view);
            childController = loader.getController();
            childController.setNavigationController(this);
        } catch (IOException e)
        {
            e.printStackTrace();
            log.error("Loading view failed. " + navigationItem.getFxmlUrl());
        }
        return childController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void init()
    {
        messageFacade.init(new BootstrapListener()
        {
            @Override
            public void onCompleted()
            {
                messageFacadeInited = true;
                if (walletFacadeInited) initialisationDone();
            }

            @Override
            public void onFailed(Throwable throwable)
            {
            }
        });

        walletFacade.addDownloadListener(new WalletFacade.DownloadListener()
        {
            @Override
            public void progress(double percent)
            {
                networkSyncPane.setProgress(percent);
            }

            @Override
            public void doneDownload()
            {
                networkSyncPane.doneDownload();
            }
        });
        walletFacade.initialize(() -> {
            walletFacadeInited = true;
            if (messageFacadeInited) initialisationDone();
        });

        trading.addTakeOfferRequestListener(this::onTakeOfferRequested);
    }

    private void initialisationDone()
    {
        addNavigation();

        Transitions.fadeOutAndRemove(loadingLabel);
        Transitions.fadeOutAndRemove(loadingBar);

        Transitions.fadeIn(leftNavPane);
        Transitions.fadeIn(rightNavPane);
        Transitions.fadeIn(contentPane);

        NavigationItem selectedNavigationItem = (NavigationItem) persistence.read(this, "selectedNavigationItem");
        if (selectedNavigationItem == null)
        {
            selectedNavigationItem = NavigationItem.HOME;
        }

        navigateToView(selectedNavigationItem);
    }

    //TODO make ordersButton also reacting to jump to pending tab
    private void onTakeOfferRequested(String offerId, PeerAddress sender)
    {
        final Button alertButton = new Button("", Icons.getIconImageView(Icons.MSG_ALERT));
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

    private void addNavigation()
    {
        homeButton = addNavButton(leftNavPane, "Overview", NavigationItem.HOME);

        buyButton = addNavButton(leftNavPane, "Buy BTC", NavigationItem.BUY);

        sellButton = addNavButton(leftNavPane, "Sell BTC", NavigationItem.SELL);

        ordersButtonButtonHolder = new Pane();
        ordersButton = addNavButton(ordersButtonButtonHolder, "Orders", NavigationItem.ORDERS);
        leftNavPane.getChildren().add(ordersButtonButtonHolder);

        fundsButton = addNavButton(leftNavPane, "Funds", NavigationItem.FUNDS);

        final Pane msgButtonHolder = new Pane();
        msgButton = addNavButton(msgButtonHolder, "Message", NavigationItem.MSG);
        leftNavPane.getChildren().add(msgButtonHolder);

        addBalanceInfo(rightNavPane);
        addAccountComboBox(rightNavPane);

        settingsButton = addNavButton(rightNavPane, "Settings", NavigationItem.SETTINGS);
    }


    private ToggleButton addNavButton(Pane parent, String title, NavigationItem navigationItem)
    {
        final Pane pane = new Pane();
        pane.setPrefSize(50, 50);
        final ToggleButton toggleButton = new ToggleButton("", Icons.getIconImageView(navigationItem.getIcon()));
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
            {
                ((MarketController) childController).setDirection(navigationItem == NavigationItem.BUY ? Direction.BUY : Direction.SELL);
            }

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

    private void addBalanceInfo(Pane parent)
    {
        final TextField balanceTextField = new TextField();
        balanceTextField.setEditable(false);
        balanceTextField.setPrefWidth(90);
        balanceTextField.setId("nav-balance-label");
        balanceTextField.setText(walletFacade.getWalletBalance().toFriendlyString());
        walletFacade.addBalanceListener(new BalanceListener()
        {
            @Override
            public void onBalanceChanged(Coin balance)
            {
                balanceTextField.setText(balance.toFriendlyString());
            }
        });

        final Label balanceCurrencyLabel = new Label("BTC");
        balanceCurrencyLabel.setPadding(new Insets(6, 0, 0, 0));

        final HBox hBox = new HBox();
        hBox.setSpacing(2);
        hBox.getChildren().setAll(balanceTextField, balanceCurrencyLabel);

        final Label titleLabel = new Label("Balance");
        titleLabel.setMouseTransparent(true);
        titleLabel.setPrefWidth(90);
        titleLabel.setId("nav-button-label");

        final VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 0, 0, 0));
        vBox.setSpacing(2);
        vBox.getChildren().setAll(hBox, titleLabel);
        parent.getChildren().add(vBox);
    }

    private void addAccountComboBox(Pane parent)
    {
        if (user.getBankAccounts().size() > 1)
        {
            final ComboBox<BankAccount> accountComboBox = new ComboBox<>(FXCollections.observableArrayList(user.getBankAccounts()));
            accountComboBox.setLayoutY(12);
            accountComboBox.setValue(user.getCurrentBankAccount());
            accountComboBox.valueProperty().addListener((ov, oldValue, newValue) -> user.setCurrentBankAccount(newValue));
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


            final Label titleLabel = new Label("Bank account");
            titleLabel.setMouseTransparent(true);
            titleLabel.setPrefWidth(90);
            titleLabel.setId("nav-button-label");

            final VBox vBox = new VBox();
            vBox.setPadding(new Insets(12, 0, 0, 0));
            vBox.setSpacing(2);
            vBox.getChildren().setAll(accountComboBox, titleLabel);
            parent.getChildren().add(vBox);
        }
    }
}