package io.bitsquare.gui.trade.orderbook;

import com.google.inject.Inject;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.trade.offer.CreateOfferController;
import io.bitsquare.gui.trade.tradeprocess.TradeProcessController;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.OrderBook;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class OrderBookController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(OrderBookController.class);
    private NavigationController navigationController;
    private OrderBook orderBook;

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
    public TextField volume, amount, price;
    @FXML
    public TableView<OrderBookListItem> orderBookTable;
    @FXML
    public TableColumn priceColumn, amountColumn, volumeColumn;
    @FXML
    private TableColumn<OrderBookListItem, OrderBookListItem> directionColumn, countryColumn, bankAccountTypeColumn;

    @Inject
    public OrderBookController(OrderBook orderBook, OrderBookFilter orderBookFilter)
    {
        this.orderBook = orderBook;
        this.orderBookFilter = orderBookFilter;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        setCountryColumnCellFactory();
        setBankAccountTypeColumnCellFactory();
        setDirectionColumnCellFactory();

        amount.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                orderBookFilter.setAmount(textInputToNumber(oldValue, newValue));
                updateVolume();
            }
        });

        price.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                orderBookFilter.setPrice(textInputToNumber(oldValue, newValue));
                updateVolume();
            }
        });

        orderBookTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            selectedOrderBookListItem = orderBookTable.getSelectionModel().getSelectedItem();
        });

        orderBookFilter.getChangedProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
            {
                updateOfferList();
            }
        });

        updateOfferList();
    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    public void setDirection(Direction direction)
    {
        orderBookTable.getSelectionModel().clearSelection();
        price.setText("");
        orderBookFilter.setDirection(direction);
    }

    private void openTradeTab(OrderBookListItem orderBookListItem)
    {
        String title = orderBookListItem.getOffer().getDirection() == Direction.BUY ? "Trade: Sell Bitcoin" : "Trade: Buy Bitcoin";
        TradeProcessController tradeProcessController = (TradeProcessController) navigationController.navigateToView(NavigationController.TRADE__PROCESS, title);

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
            holderPane.setBottomAnchor(createOfferButton, 360.0);
            holderPane.setLeftAnchor(createOfferButton, 10.0);
            holderPane.getChildren().add(createOfferButton);

            createOfferButton.setOnAction(e -> {
                ChildController nextController = navigationController.navigateToView(NavigationController.TRADE__CREATE_OFFER, "Create offer");
                ((CreateOfferController) nextController).setOrderBookFilter(orderBookFilter);
            });
        }
        createOfferButton.setVisible(true);

        holderPane.setBottomAnchor(orderBookTable, 390.0);
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

    private void setDirectionColumnCellFactory()
    {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        directionColumn.setCellFactory(new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem, OrderBookListItem>>()
        {
            @Override
            public TableCell<OrderBookListItem, OrderBookListItem> call(TableColumn<OrderBookListItem, OrderBookListItem> directionColumn)
            {
                return new TableCell<OrderBookListItem, OrderBookListItem>()
                {
                    final ImageView iconView = new ImageView();
                    final Button button = new Button();

                    {
                        button.setGraphic(iconView);
                        button.setMinWidth(70);
                    }

                    @Override
                    public void updateItem(final OrderBookListItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        if (orderBookListItem != null)
                        {
                            String title;
                            Image icon;
                            if (orderBookListItem.getOffer().getDirection() == Direction.SELL)
                            {
                                icon = buyIcon;
                                title = Formatter.formatDirection(Direction.BUY, true);
                            }
                            else
                            {
                                icon = sellIcon;
                                title = Formatter.formatDirection(Direction.SELL, true);
                            }
                            iconView.setImage(icon);
                            button.setText(title);
                            setGraphic(button);

                            button.setOnAction(event -> openTradeTab(orderBookListItem));
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }

    private void setCountryColumnCellFactory()
    {
        countryColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        countryColumn.setCellFactory(new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem, OrderBookListItem>>()
        {
            @Override
            public TableCell<OrderBookListItem, OrderBookListItem> call(TableColumn<OrderBookListItem, OrderBookListItem> directionColumn)
            {
                return new TableCell<OrderBookListItem, OrderBookListItem>()
                {
                    final HBox hBox = new HBox();

                    {
                        hBox.setSpacing(3);
                        hBox.setAlignment(Pos.CENTER);
                        setGraphic(hBox);
                    }

                    @Override
                    public void updateItem(final OrderBookListItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        if (orderBookListItem != null)
                        {
                            hBox.getChildren().clear();
                            setText("");

                            List<Locale> countryLocales = orderBookListItem.getOffer().getOfferConstraints().getCountryLocales();
                            int i = 0;
                            String countries = "";
                            for (Locale countryLocale : countryLocales)
                            {
                                countries += countryLocale.getDisplayCountry();
                                if (i < countryLocales.size() - 1)
                                    countries += ", ";

                                if (i < 4)
                                {
                                    try
                                    {
                                        ImageView imageView = Icons.getIconImageView("/images/countries/" + countryLocale.getCountry().toLowerCase() + ".png");
                                        hBox.getChildren().add(imageView);

                                    } catch (Exception e)
                                    {
                                        log.warn("Country icon not found: " + "/images/countries/" + countryLocale.getCountry().toLowerCase() + ".png country name: " + countryLocale.getDisplayCountry());
                                    }
                                }
                                else
                                {
                                    setText("...");
                                }
                                i++;
                            }
                            Tooltip.install(this, new Tooltip(countries));
                        }
                        else
                        {
                            setText("");
                            hBox.getChildren().clear();
                        }
                    }
                };
            }
        });
    }

    private void setBankAccountTypeColumnCellFactory()
    {
        bankAccountTypeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        bankAccountTypeColumn.setCellFactory(new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem, OrderBookListItem>>()
        {
            @Override
            public TableCell<OrderBookListItem, OrderBookListItem> call(TableColumn<OrderBookListItem, OrderBookListItem> directionColumn)
            {
                return new TableCell<OrderBookListItem, OrderBookListItem>()
                {
                    @Override
                    public void updateItem(final OrderBookListItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        if (orderBookListItem != null)
                        {
                            List<BankAccountType.BankAccountTypeEnum> bankAccountTypeEnums = orderBookListItem.getOffer().getOfferConstraints().getBankAccountTypes();
                            String text = "";
                            int i = 0;
                            for (BankAccountType.BankAccountTypeEnum bankAccountTypeEnum : bankAccountTypeEnums)
                            {
                                text += Localisation.get(bankAccountTypeEnum.toString());
                                i++;
                                if (i < bankAccountTypeEnums.size())
                                    text += ", ";
                            }
                            setText(text);
                            if (text.length() > 20)
                                Tooltip.install(this, new Tooltip(text));
                        }
                        else
                        {
                            setText("");
                        }
                    }
                };
            }
        });
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

