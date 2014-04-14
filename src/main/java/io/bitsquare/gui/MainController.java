package io.bitsquare.gui;

import com.google.bitcoin.core.DownloadListener;
import com.google.inject.Inject;
import io.bitsquare.BitSquare;
import io.bitsquare.btc.IWalletFacade;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.trade.TradeController;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Currency;
import java.util.Date;
import java.util.ResourceBundle;

public class MainController implements Initializable, INavigationController
{
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private Settings settings;
    private OrderBookFilter orderBookFilter;
    private IWalletFacade walletFacade;
    private IChildController childController;
    private ToggleGroup toggleGroup;
    private ToggleButton prevToggleButton;
    private Image prevToggleButtonIcon;
    public ProgressBar networkSyncProgressBar;
    public Label networkSyncInfoLabel;

    @FXML
    public Pane contentPane;
    public HBox leftNavPane, rightNavPane, footerContainer;

    @Inject
    public MainController(Settings settings, OrderBookFilter orderBookFilter, IWalletFacade walletFacade)
    {
        this.settings = settings;
        this.orderBookFilter = orderBookFilter;
        this.walletFacade = walletFacade;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        buildNavigation();

        buildFooter();

        walletFacade.initWallet(new ProgressBarUpdater());
    }


    @Override
    public IChildController navigateToView(String fxmlView, String title)
    {
        FXMLLoader loader = new GuiceFXMLLoader();
        try
        {
            Node view = loader.load(BitSquare.class.getResourceAsStream(fxmlView));
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

    public IChildController navigateToView(String fxmlView, Direction direction)
    {
        childController = navigateToView(fxmlView, direction == Direction.BUY ? "Orderbook Buy" : "Orderbook Sell");
        if (childController instanceof TradeController && direction != null)
        {
            ((TradeController) childController).setDirection(direction);
        }
        return childController;
    }

    private void buildNavigation()
    {
        toggleGroup = new ToggleGroup();

        ToggleButton homeButton = addNavButton(leftNavPane, "Overview", Icons.HOME, Icons.HOME, INavigationController.HOME);
        ToggleButton buyButton = addNavButton(leftNavPane, "Buy BTC", Icons.NAV_BUY, Icons.NAV_BUY_ACTIVE, INavigationController.TRADE, Direction.BUY);
        ToggleButton sellButton = addNavButton(leftNavPane, "Sell BTC", Icons.NAV_SELL, Icons.NAV_SELL_ACTIVE, INavigationController.TRADE, Direction.SELL);
        addNavButton(leftNavPane, "Orders", Icons.ORDERS, Icons.ORDERS, INavigationController.ORDERS);
        addNavButton(leftNavPane, "History", Icons.HISTORY, Icons.HISTORY, INavigationController.HISTORY);
        addNavButton(leftNavPane, "Funds", Icons.FUNDS, Icons.FUNDS, INavigationController.FUNDS);
        addNavButton(leftNavPane, "Message", Icons.MSG, Icons.MSG, INavigationController.MSG);
        addCurrencyComboBox();
        addNavButton(rightNavPane, "Settings", Icons.SETTINGS, Icons.SETTINGS, INavigationController.SETTINGS);

        sellButton.fire();
        //homeButton.fire();
    }

    private void buildFooter()
    {
        networkSyncInfoLabel = new Label();
        networkSyncInfoLabel.setText("Synchronize with network...");
        networkSyncProgressBar = new ProgressBar();
        networkSyncProgressBar.setPrefWidth(200);
        networkSyncProgressBar.setProgress(-1);
        footerContainer.getChildren().addAll(networkSyncProgressBar, networkSyncInfoLabel);
    }

    private ToggleButton addNavButton(Pane parent, String title, String iconId, String iconIdActivated, String navTarget)
    {
        return addNavButton(parent, title, iconId, iconIdActivated, navTarget, null);
    }

    private ToggleButton addNavButton(Pane parent, String title, String iconId, String iconIdActivated, String navTarget, Direction direction)
    {
        VBox vBox = new VBox();
        ToggleButton toggleButton = new ToggleButton("", Icons.getIconImageView(iconId));
        toggleButton.setPrefWidth(50);
        toggleButton.setToggleGroup(toggleGroup);
        Label titleLabel = new Label(title);
        titleLabel.setPrefWidth(50);

        toggleButton.setId("nav-button");
        titleLabel.setId("nav-button-label");

        vBox.getChildren().setAll(toggleButton, titleLabel);
        parent.getChildren().add(vBox);

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
        return toggleButton;
    }

    private void addCurrencyComboBox()
    {
        Pane holder = new Pane();
        ComboBox currencyComboBox = new ComboBox(FXCollections.observableArrayList(settings.getAllCurrencies()));
        currencyComboBox.setLayoutY(10);
        currencyComboBox.setId("nav-currency-combobox");
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

    private void setProgress(double percent)
    {
        networkSyncProgressBar.setProgress(percent / 100.0);
        networkSyncInfoLabel.setText("Synchronize with network: " + (int) percent + "%");
    }

    private void doneDownload()
    {
        networkSyncInfoLabel.setText("Sync with network: Done");

        FadeTransition fade = new FadeTransition(Duration.millis(700), footerContainer);
        fade.setToValue(0.0);
        fade.setCycleCount(1);
        fade.setInterpolator(Interpolator.EASE_BOTH);
        fade.play();
        fade.setOnFinished(e -> footerContainer.getChildren().clear());
    }

    private class ProgressBarUpdater extends DownloadListener
    {
        @Override
        protected void progress(double percent, int blocksSoFar, Date date)
        {
            super.progress(percent, blocksSoFar, date);
            Platform.runLater(() -> MainController.this.setProgress(percent));
        }

        @Override
        protected void doneDownload()
        {
            super.doneDownload();
            Platform.runLater(MainController.this::doneDownload);
        }

    }
}