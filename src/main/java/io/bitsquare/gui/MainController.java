package io.bitsquare.gui;

import com.google.inject.Inject;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.setup.SetupController;
import io.bitsquare.gui.trade.TradeController;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;
import javafx.application.Platform;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Currency;
import java.util.Date;
import java.util.ResourceBundle;

public class MainController implements Initializable, NavigationController, WalletFacade.DownloadListener
{
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private Settings settings;
    private User user;
    private OrderBookFilter orderBookFilter;
    private WalletFacade walletFacade;
    private ChildController childController;
    private ToggleGroup toggleGroup;
    private ToggleButton prevToggleButton;
    private Image prevToggleButtonIcon;
    // public ProgressBar networkSyncProgressBar;
    //public Label networkSyncInfoLabel;
    private Pane setupView;
    private SetupController setupController;

    @FXML
    public Pane contentPane;
    public HBox leftNavPane, rightNavPane;
    public StackPane rootContainer;
    public AnchorPane anchorPane;
    private NetworkSyncPane networkSyncPane;


    @Inject
    public MainController(Settings settings, User user, OrderBookFilter orderBookFilter, WalletFacade walletFacade)
    {
        this.settings = settings;
        this.user = user;
        this.orderBookFilter = orderBookFilter;
        this.walletFacade = walletFacade;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        networkSyncPane = new NetworkSyncPane();
        networkSyncPane.setSpacing(10);
        networkSyncPane.setPrefHeight(20);
        AnchorPane.setBottomAnchor(networkSyncPane, 0.0);
        AnchorPane.setLeftAnchor(networkSyncPane, 0.0);

        walletFacade.addDownloadListener(this);
        walletFacade.initWallet();

        buildNavigation();
        if (user.getAccountID() != null)
        {
            anchorPane.getChildren().add(networkSyncPane);
        }
        else
        {
            buildSetupView();
            anchorPane.setOpacity(0);
            setupController.setNetworkSyncPane(networkSyncPane);
            rootContainer.getChildren().add(setupView);
        }
    }


    @Override
    public ChildController navigateToView(String fxmlView, String title)
    {
        if (setupView != null)
        {
            anchorPane.getChildren().add(networkSyncPane);

            anchorPane.setOpacity(1);
            rootContainer.getChildren().remove(setupView);
            setupView = null;
            setupController = null;

            return null;
        }

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

    @Override
    public void progress(double percent, int blocksSoFar, Date date)
    {
        if (networkSyncPane != null)
            Platform.runLater(() -> networkSyncPane.setProgress(percent));
    }

    @Override
    public void doneDownload()
    {
        if (networkSyncPane != null)
            Platform.runLater(networkSyncPane::doneDownload);
    }

    public ChildController navigateToView(String fxmlView, Direction direction)
    {
        childController = navigateToView(fxmlView, direction == Direction.BUY ? "Orderbook Buy" : "Orderbook Sell");
        if (childController instanceof TradeController && direction != null)
        {
            ((TradeController) childController).setDirection(direction);
        }
        return childController;
    }

    private void buildSetupView()
    {
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(NavigationController.SETUP), Localisation.getResourceBundle());
        try
        {
            setupView = loader.load();
            setupController = loader.getController();
            setupController.setNavigationController(this);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void buildNavigation()
    {
        toggleGroup = new ToggleGroup();

        ToggleButton homeButton = addNavButton(leftNavPane, "Overview", Icons.HOME, Icons.HOME, NavigationController.HOME);
        ToggleButton buyButton = addNavButton(leftNavPane, "Buy BTC", Icons.NAV_BUY, Icons.NAV_BUY_ACTIVE, NavigationController.TRADE, Direction.BUY);
        ToggleButton sellButton = addNavButton(leftNavPane, "Sell BTC", Icons.NAV_SELL, Icons.NAV_SELL_ACTIVE, NavigationController.TRADE, Direction.SELL);
        addNavButton(leftNavPane, "Orders", Icons.ORDERS, Icons.ORDERS, NavigationController.ORDERS);
        addNavButton(leftNavPane, "History", Icons.HISTORY, Icons.HISTORY, NavigationController.HISTORY);
        addNavButton(leftNavPane, "Funds", Icons.FUNDS, Icons.FUNDS, NavigationController.FUNDS);
        addNavButton(leftNavPane, "Message", Icons.MSG, Icons.MSG, NavigationController.MSG);
        addBalanceInfo(rightNavPane);
        addCurrencyComboBox();

        addNavButton(rightNavPane, "Settings", Icons.SETTINGS, Icons.SETTINGS, NavigationController.SETTINGS);

        sellButton.fire();
        //homeButton.fire();
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

            if (childController instanceof TradeController && direction != null)
            {
                ((TradeController) childController).setDirection(direction);
            }
            else
                navigateToView(navTarget, direction);

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
        Pane holder = new Pane();
        TextField balanceLabel = new TextField();
        balanceLabel.setEditable(false);
        balanceLabel.setMouseTransparent(true);
        balanceLabel.setPrefWidth(90);
        balanceLabel.setId("nav-balance-label");
        balanceLabel.setText(Formatter.formatSatoshis(walletFacade.getBalance(), false));
        holder.getChildren().add(balanceLabel);
        rightNavPane.getChildren().add(holder);

        Label balanceCurrencyLabel = new Label("BTC");
        balanceCurrencyLabel.setPadding(new Insets(6, 0, 0, 0));
        HBox hBox = new HBox();
        hBox.setSpacing(2);
        hBox.getChildren().setAll(balanceLabel, balanceCurrencyLabel);

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 0, 0, 0));
        vBox.setSpacing(2);
        Label titleLabel = new Label("Balance");
        titleLabel.setMouseTransparent(true);
        titleLabel.setPrefWidth(90);
        titleLabel.setId("nav-button-label");

        vBox.getChildren().setAll(hBox, titleLabel);
        parent.getChildren().add(vBox);

        return balanceLabel;
    }

    private void addCurrencyComboBox()
    {
        Pane holder = new Pane();
        ComboBox currencyComboBox = new ComboBox(FXCollections.observableArrayList(settings.getAllCurrencies()));
        currencyComboBox.setLayoutY(12);
        currencyComboBox.setValue(Settings.getCurrency());

        currencyComboBox.valueProperty().addListener(new ChangeListener<Currency>()
        {
            @Override
            public void changed(ObservableValue ov, Currency oldValue, Currency newValue)
            {
                orderBookFilter.setCurrency(newValue);
                settings.setCurrency(newValue);
            }
        });
        holder.getChildren().add(currencyComboBox);
        rightNavPane.getChildren().add(holder);
    }


}