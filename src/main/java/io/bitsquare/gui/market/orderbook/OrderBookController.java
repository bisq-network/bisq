package io.bitsquare.gui.market.orderbook;

import com.google.inject.Inject;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.market.createOffer.CreateOfferController;
import io.bitsquare.gui.market.trade.TakerTradeController;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.orderbook.OrderBook;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;
import io.bitsquare.util.Utilities;
import javafx.animation.AnimationTimer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.SortedList;
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

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.ResourceBundle;

public class OrderBookController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(OrderBookController.class);
    private NavigationController navigationController;
    private OrderBook orderBook;

    private SortedList<OrderBookListItem> offerList;
    private final OrderBookFilter orderBookFilter;
    private User user;
    private MessageFacade messageFacade;
    private AnimationTimer pollingTimer;

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
    private TableColumn<String, OrderBookListItem> directionColumn, countryColumn, bankAccountTypeColumn;
    @FXML
    public Button createOfferButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OrderBookController(OrderBook orderBook, OrderBookFilter orderBookFilter, User user, MessageFacade messageFacade)
    {
        this.orderBook = orderBook;
        this.orderBookFilter = orderBookFilter;
        this.user = user;
        this.messageFacade = messageFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        orderBook.init();

        // setup table
        setCountryColumnCellFactory();
        setBankAccountTypeColumnCellFactory();
        setDirectionColumnCellFactory();
        offerList = orderBook.getOfferList();
        offerList.comparatorProperty().bind(orderBookTable.comparatorProperty());
        orderBookTable.setItems(offerList);
        orderBookTable.getSortOrder().add(priceColumn);
        orderBookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        orderBook.loadOffers();

        // handlers
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

        orderBookFilter.getDirectionChangedProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
            {
                applyOffers();
            }
        });

        user.getBankAccountChangedProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
            {
                orderBook.loadOffers();
            }
        });

        createOfferButton.setOnAction(e -> {
            ChildController nextController = navigationController.navigateToView(NavigationController.CREATE_OFFER, "Create offer");
            ((CreateOfferController) nextController).setOrderBookFilter(orderBookFilter);
        });

        //TODO do polling until broadcast works
        setupPolling();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void cleanup()
    {
        orderBook.cleanup();

        orderBookTable.setItems(null);
        orderBookTable.getSortOrder().clear();
        offerList.comparatorProperty().unbind();

        if (pollingTimer != null)
        {
            pollingTimer.stop();
            pollingTimer = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setDirection(Direction direction)
    {
        orderBookTable.getSelectionModel().clearSelection();
        price.setText("");
        orderBookFilter.setDirection(direction);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void takeOffer(Offer offer)
    {
        String title = offer.getDirection() == Direction.BUY ? "Trade: Sell Bitcoin" : "Trade: Buy Bitcoin";
        TakerTradeController takerTradeController = (TakerTradeController) navigationController.navigateToView(NavigationController.TAKER_TRADE, title);

        BigInteger requestedAmount = offer.getAmount();
        if (!amount.getText().equals(""))
            requestedAmount = BtcFormatter.stringValueToSatoshis(amount.getText());

        takerTradeController.initWithData(offer, requestedAmount);
    }

    private void removeOffer(Offer offer)
    {
        try
        {
            orderBook.removeOffer(offer);
        } catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private void applyOffers()
    {
        orderBook.applyFilter(orderBookFilter);

        priceColumn.setSortType((orderBookFilter.getDirection() == Direction.BUY) ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);
        orderBookTable.sort();

        if (orderBookTable.getItems() != null)
            createOfferButton.setDefaultButton(orderBookTable.getItems().size() == 0);
    }

    private void setupPolling()
    {

        pollingTimer = Utilities.setInterval(1000, (AnimationTimer animationTimer) -> {
            try
            {
                messageFacade.getDirtyFlag(user.getCurrentBankAccount().getCurrency());
            } catch (IOException e)
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            return null;
        });
        messageFacade.getIsDirtyProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> observableValue, Boolean oldValue, Boolean newValue)
            {
                //log.info("getIsDirtyProperty changed " + oldValue + "/" + newValue);
                orderBook.loadOffers();
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setDirectionColumnCellFactory()
    {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        directionColumn.setCellFactory(new Callback<TableColumn<String, OrderBookListItem>, TableCell<String, OrderBookListItem>>()
        {
            @Override
            public TableCell<String, OrderBookListItem> call(TableColumn<String, OrderBookListItem> directionColumn)
            {
                return new TableCell<String, OrderBookListItem>()
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
                            Offer offer = orderBookListItem.getOffer();

                            if (offer.getMessagePubKeyAsHex().equals(user.getMessagePubKeyAsHex()))
                            {
                                icon = Icons.getIconImage(Icons.REMOVE);
                                title = "Remove";
                                button.setOnAction(event -> removeOffer(orderBookListItem.getOffer()));
                            }
                            else
                            {
                                if (offer.getDirection() == Direction.SELL)
                                {
                                    icon = buyIcon;
                                    title = Formatter.formatDirection(Direction.BUY, true);
                                }
                                else
                                {
                                    icon = sellIcon;
                                    title = Formatter.formatDirection(Direction.SELL, true);
                                }

                                button.setDefaultButton(getIndex() == 0);
                                button.setOnAction(event -> takeOffer(orderBookListItem.getOffer()));
                            }


                            iconView.setImage(icon);
                            button.setText(title);
                            setGraphic(button);
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
        countryColumn.setCellFactory(new Callback<TableColumn<String, OrderBookListItem>, TableCell<String, OrderBookListItem>>()
        {
            @Override
            public TableCell<String, OrderBookListItem> call(TableColumn<String, OrderBookListItem> directionColumn)
            {
                return new TableCell<String, OrderBookListItem>()
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

                        hBox.getChildren().clear();
                        if (orderBookListItem != null)
                        {
                            Locale countryLocale = orderBookListItem.getOffer().getBankAccountCountryLocale();
                            try
                            {
                                hBox.getChildren().add(Icons.getIconImageView("/images/countries/" + countryLocale.getCountry().toLowerCase() + ".png"));

                            } catch (Exception e)
                            {
                                log.warn("Country icon not found: " + "/images/countries/" + countryLocale.getCountry().toLowerCase() + ".png country name: " + countryLocale.getDisplayCountry());
                            }
                            Tooltip.install(this, new Tooltip(countryLocale.getDisplayCountry()));
                        }
                    }
                };
            }
        });
    }

    private void setBankAccountTypeColumnCellFactory()
    {
        bankAccountTypeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        bankAccountTypeColumn.setCellFactory(new Callback<TableColumn<String, OrderBookListItem>, TableCell<String, OrderBookListItem>>()
        {
            @Override
            public TableCell<String, OrderBookListItem> call(TableColumn<String, OrderBookListItem> directionColumn)
            {
                return new TableCell<String, OrderBookListItem>()
                {
                    @Override
                    public void updateItem(final OrderBookListItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        if (orderBookListItem != null)
                        {
                            BankAccountType.BankAccountTypeEnum bankAccountTypeEnum = orderBookListItem.getOffer().getBankAccountTypeEnum();
                            setText(Localisation.get(bankAccountTypeEnum.toString()));
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private double textInputToNumber(String oldValue, String newValue)
    {
        //TODO use regex.... or custom textfield component
        double d = 0.0;
        if (!newValue.equals(""))
        {
            try
            {
                DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.getDefault());
                d = decimalFormat.parse(newValue).doubleValue();
            } catch (ParseException e)
            {
                amount.setText(oldValue);
                d = Converter.stringToDouble(oldValue);
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

