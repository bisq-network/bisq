package io.bitsquare.gui.trade.orderbook;

import com.google.inject.Inject;
import io.bitsquare.gui.IChildController;
import io.bitsquare.gui.INavigationController;
import io.bitsquare.gui.trade.offer.CreateOfferController;
import io.bitsquare.gui.trade.tradeprocess.TradeProcessController;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.IOrderBook;
import io.bitsquare.trade.orderbook.MockOrderBook;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.ResourceBundle;

public class OrderBookController implements Initializable, IChildController
{
    private INavigationController navigationController;
    private IOrderBook orderBook;
    private Settings settings;

    private OrderBookListItem selectedOrderBookListItem;
    private final OrderBookFilter orderBookFilter;

    private Button createOfferButton;
    private Image buyIcon = Icons.getIconImage(Icons.BUY);
    private Image sellIcon = Icons.getIconImage(Icons.SELL);

    @FXML
    public AnchorPane holderPane;
    @FXML
    public HBox topHBox;
    @FXML
    private Button tradeButton;
    @FXML
    public TextField volume, amount, price;
    @FXML
    public Pane filterPane;
    @FXML
    public TableView<OrderBookListItem> orderBookTable;
    @FXML
    public TableColumn priceColumn, amountColumn, volumeColumn;
    @FXML
    private ImageView tradeButtonImageView;

    @Inject
    public OrderBookController(IOrderBook orderBook, OrderBookFilter orderBookFilter, Settings settings)
    {
        this.orderBook = orderBook;
        this.orderBookFilter = orderBookFilter;
        this.settings = settings;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        orderBookFilter.getCurrencyProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                updateOfferList();
            }
        });

        createFilterPane();

        updateOfferList();

        amount.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                orderBookFilter.setAmount(textInputToNumber(oldValue, newValue));
                updateOfferList();
                updateVolume();
            }
        });

        price.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                orderBookFilter.setPrice(textInputToNumber(oldValue, newValue));
                updateOfferList();
                updateVolume();
            }
        });

        orderBookTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            selectedOrderBookListItem = orderBookTable.getSelectionModel().getSelectedItem();
            tradeButton.setDisable(selectedOrderBookListItem == null);
        });

        tradeButton.setOnAction(e -> openTradeTab(selectedOrderBookListItem));
        tradeButton.setDisable(true);
        tradeButton.setDefaultButton(true);
    }

    @Override
    public void setNavigationController(INavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    public void setDirection(Direction direction)
    {
        orderBookTable.getSelectionModel().clearSelection();
        tradeButton.setDisable(true);
        price.setText("");

        String title;
        Image icon;
        if (direction == Direction.SELL)
        {
            title = "SELL";
            icon = sellIcon;
        }
        else
        {
            title = "BUY";
            icon = buyIcon;
        }
        tradeButton.setText(title);
        tradeButtonImageView.setImage(icon);
        orderBookFilter.setDirection(direction);

        updateOfferList();
    }

    private void openTradeTab(OrderBookListItem orderBookListItem)
    {
        String title = orderBookListItem.getOffer().getDirection() == Direction.BUY ? "Trade: Sell Bitcoin" : "Trade: Buy Bitcoin";
        TradeProcessController tradeProcessController = (TradeProcessController) navigationController.navigateToView(INavigationController.TRADE__PROCESS, title);

        double requestedAmount = orderBookListItem.getOffer().getAmount();
        if (!amount.getText().equals(""))
            requestedAmount = Converter.convertToDouble(amount.getText());

        tradeProcessController.initView(orderBookListItem.getOffer(), requestedAmount);
    }

    private void displayCreateOfferButton()
    {
        if (createOfferButton == null)
        {
            createOfferButton = new Button("Create new offer");
            holderPane.setBottomAnchor(createOfferButton, 375.0);
            holderPane.setLeftAnchor(createOfferButton, 200.0);
            holderPane.getChildren().add(createOfferButton);

            createOfferButton.setOnAction(e -> {
                IChildController nextController = navigationController.navigateToView(INavigationController.TRADE__CREATE_OFFER, "Create offer");
                ((CreateOfferController) nextController).setOrderBookFilter(orderBookFilter);
            });
        }
        createOfferButton.setVisible(true);

        holderPane.setBottomAnchor(orderBookTable, 410.0);
    }

    private void updateOfferList()
    {
        ObservableList offers = orderBook.getFilteredList(orderBookFilter);
        orderBookTable.setItems(offers);
        orderBookTable.getSortOrder().add(priceColumn);
        priceColumn.setSortType((orderBookFilter.getDirection() == Direction.BUY) ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);

        if (offers.size() == 0)
        {
            displayCreateOfferButton();
        }
        else if (createOfferButton != null)
        {
            createOfferButton.setVisible(false);
            holderPane.setBottomAnchor(orderBookTable, 10.0);
        }
    }

    private void createFilterPane()
    {
        MockOrderBook mockOrderBook = new MockOrderBook(settings);
        orderBookFilter.setOfferConstraints(mockOrderBook.getRandomOfferConstraints());

        OrderBookFilterTextItemBuilder.build(filterPane, "Bank transfer types: ", orderBookFilter.getOfferConstraints().getBankTransferTypes(), settings.getAllBankTransferTypes());
        OrderBookFilterTextItemBuilder.build(filterPane, "Countries: ", orderBookFilter.getOfferConstraints().getCountries(), settings.getAllCountries());
        OrderBookFilterTextItemBuilder.build(filterPane, "Languages: ", orderBookFilter.getOfferConstraints().getLanguages(), settings.getAllLanguages());
        OrderBookFilterTextItemBuilder.build(filterPane, "Collateral: ", Arrays.asList(String.valueOf(orderBookFilter.getOfferConstraints().getCollateral())), settings.getAllCollaterals());
        OrderBookFilterTextItemBuilder.build(filterPane, "Arbitrator: ", Arrays.asList(orderBookFilter.getOfferConstraints().getArbitrator()), settings.getAllArbitrators());
    }

    private double textInputToNumber(String oldValue, String newValue)
    {
        //TODO use regex.... or custom textfield component
        double d = 0.0;
        if (!newValue.equals(""))
        {
            try
            {
                DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Settings.getLocale());
                d = decimalFormat.parse(newValue).doubleValue();
            } catch (ParseException e)
            {
                amount.setText(oldValue);
                d = Converter.convertToDouble(oldValue);
            }
        }
        return d;
    }

    private void updateVolume()
    {
        double a = textInputToNumber(amount.getText(), amount.getText());
        double p = textInputToNumber(price.getText(), price.getText());
        volume.setText(Formatter.formatPrice(a * p));
    }

}

