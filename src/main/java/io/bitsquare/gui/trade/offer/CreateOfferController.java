package io.bitsquare.gui.trade.offer;

import com.google.inject.Inject;
import io.bitsquare.gui.IChildController;
import io.bitsquare.gui.INavigationController;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.settings.OrderBookFilterSettings;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.OfferConstraints;
import io.bitsquare.trade.TradingFacade;
import io.bitsquare.trade.orderbook.MockOrderBook;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Currency;
import java.util.ResourceBundle;
import java.util.UUID;

public class CreateOfferController implements Initializable, IChildController
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferController.class);

    private INavigationController navigationController;
    private TradingFacade tradingFacade;
    private OrderBookFilterSettings orderBookFilterSettings;
    private Settings settings;
    private User user;
    private double filterPaneItemOffset;
    private Direction direction;

    @FXML
    public AnchorPane holderPane;
    @FXML
    public Pane detailsPane;

    @FXML
    public Label buyLabel;
    @FXML
    public TextField volume;
    @FXML
    public ImageView directionImageView;

    @FXML
    public TextField amount;
    @FXML
    public TextField price;
    @FXML
    public TextField minAmount;
    @FXML
    public Button placeOfferButton;

    @Inject
    public CreateOfferController(TradingFacade tradingFacade, OrderBookFilterSettings orderBookFilterSettings, Settings settings, User user)
    {
        this.tradingFacade = tradingFacade;
        this.orderBookFilterSettings = orderBookFilterSettings;
        this.settings = settings;
        this.user = user;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        createFilterPane();

        amount.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                setVolume();
            }
        });

        price.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                setVolume();
            }
        });

        placeOfferButton.setOnAction(e -> {
            // TODO not impl yet. use mocks
            OfferConstraints offerConstraints = new MockOrderBook(settings).getRandomOfferConstraints();
            Offer offer = new Offer(UUID.randomUUID(),
                    direction,
                    Converter.convertToDouble(price.getText()),
                    Converter.convertToDouble(amount.getText()),
                    Converter.convertToDouble(minAmount.getText()),
                    settings.getCurrency(),
                    user,
                    offerConstraints);
            tradingFacade.placeNewOffer(offer);

            TabPane tabPane = ((TabPane) (holderPane.getParent().getParent()));
            tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

            navigationController.navigateToView(INavigationController.TRADE__ORDER_BOOK, "Orderbook");
        });
    }

    @Override
    public void setNavigationController(INavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    public void setOrderBookFilter(OrderBookFilter orderBookFilter)
    {
        direction = orderBookFilter.getDirection();
        amount.setText(Formatter.formatPrice(orderBookFilter.getAmount()));
        minAmount.setText(Formatter.formatPrice(orderBookFilter.getAmount()));
        price.setText(Formatter.formatPrice(orderBookFilter.getPrice()));

        configDirection();
    }

    private void configDirection()
    {
        String iconPath;
        String buyLabelText;
        if (direction == Direction.BUY)
        {
            iconPath = "/images/buy.png";
            buyLabelText = "BUY";
        }
        else
        {
            iconPath = "/images/sell.png";
            buyLabelText = "SELL";
        }
        Image icon = new Image(getClass().getResourceAsStream(iconPath));
        directionImageView.setImage(icon);
        buyLabel.setText(buyLabelText);
    }

    private void createFilterPane()
    {
        filterPaneItemOffset = 30;

        ArrayList<Currency> currencies = orderBookFilterSettings.getCurrencies();
        Currency currency = orderBookFilterSettings.getCurrency();
        ComboBox currencyComboBox = createCurrencyItem("Currency: ", currency, currencies);
        currencyComboBox.valueProperty().addListener(new ChangeListener<Currency>()
        {
            @Override
            public void changed(ObservableValue ov, Currency oldValue, Currency newValue)
            {
                orderBookFilterSettings.setCurrency(newValue);
            }
        });

        Label bankLabel = createFilterItem("Bank transfer types: ", "SEPA, OKPAY");

        Label countriesLabel = createFilterItem("Countries: ", "DE, GB, AT");
        Label languagesLabel = createFilterItem("Languages: ", "DE, EN");
        Label arbitratorsLabel = createFilterItem("Arbitrators: ", "Paysty, BitRated");
        Label identityLabel = createFilterItem("Identity verifications: ", "Passport, Google+, Facebook, Skype");
        TextField collateralLabel = createCollateralItem("Collateral (%): ", 10);
    }

    private ComboBox createCurrencyItem(String labelText, Currency currency, ArrayList<Currency> currencies)
    {
        final Separator separator = new Separator();
        separator.setPrefWidth(380);
        separator.setLayoutY(0 + filterPaneItemOffset);
        separator.setLayoutX(0);
        final Label label = new Label(labelText);
        label.setLayoutY(10 + filterPaneItemOffset);
        ObservableList<Currency> options = FXCollections.observableArrayList(currencies);
        final ComboBox comboBox = new ComboBox(options);
        comboBox.setLayoutX(70);
        comboBox.setLayoutY(5 + filterPaneItemOffset);
        comboBox.setValue(currency);


        detailsPane.getChildren().addAll(separator, label, comboBox);
        filterPaneItemOffset += 40;
        return comboBox;
    }

    private Label createFilterItem(String labelText, String valueText)
    {
        final Separator separator = new Separator();
        separator.setPrefWidth(380);
        separator.setLayoutY(0 + filterPaneItemOffset);
        separator.setLayoutX(0);
        final Label label = new Label(labelText + valueText);
        label.setLayoutY(10 + filterPaneItemOffset);
        label.setPrefWidth(310);
        Tooltip tooltip = new Tooltip(valueText);
        label.setTooltip(tooltip);

        final Button edit = new Button("Edit");
        edit.setPrefWidth(50);
        edit.setLayoutX(330);
        edit.setLayoutY(5 + filterPaneItemOffset);

        detailsPane.getChildren().addAll(separator, label, edit);
        filterPaneItemOffset += 40;
        return label;
    }

    private TextField createCollateralItem(String labelText, double collateral)
    {
        final Separator separator = new Separator();
        separator.setPrefWidth(380);
        separator.setLayoutY(0 + filterPaneItemOffset);
        separator.setLayoutX(0);
        final Label label = new Label(labelText);
        label.setLayoutY(10 + filterPaneItemOffset);
        label.setPrefWidth(310);

        final TextField collateralValue = new TextField(Double.toString(collateral));
        collateralValue.setLayoutX(90);
        collateralValue.setLayoutY(5 + filterPaneItemOffset);
        collateralValue.setPrefWidth(50);

        detailsPane.getChildren().addAll(separator, label, collateralValue);
        filterPaneItemOffset += 40;

        return collateralValue;
    }


    private double textInputToNumber(String oldValue, String newValue)
    {
        //TODO use regex.... or better custom textfield component
        double d = 0.0;
        if (!newValue.equals(""))
        {
            d = Converter.convertToDouble(newValue);
            if (d == Double.NEGATIVE_INFINITY)
            {
                amount.setText(oldValue);
                d = Converter.convertToDouble(oldValue);
            }
        }
        return d;
    }

    private void setVolume()
    {
        double a = textInputToNumber(amount.getText(), amount.getText());
        double p = textInputToNumber(price.getText(), price.getText());
        volume.setText(Formatter.formatPrice(a * p));
    }


}

