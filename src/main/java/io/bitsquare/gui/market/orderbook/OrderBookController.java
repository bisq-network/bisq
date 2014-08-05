package io.bitsquare.gui.market.orderbook;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.MainController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.market.createOffer.CreateOfferController;
import io.bitsquare.gui.market.trade.TakerOfferController;
import io.bitsquare.gui.popups.Popups;
import io.bitsquare.gui.util.BitSquareConverter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.Localisation;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Persistence;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.orderbook.OrderBook;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;
import io.bitsquare.util.Utilities;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.animation.AnimationTimer;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
import javax.inject.Inject;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderBookController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(OrderBookController.class);
    private final OrderBook orderBook;
    private final OrderBookFilter orderBookFilter;
    private final User user;
    private final MessageFacade messageFacade;
    private final WalletFacade walletFacade;
    private final Settings settings;
    private final Persistence persistence;

    private final Image buyIcon = Icons.getIconImage(Icons.BUY);
    private final Image sellIcon = Icons.getIconImage(Icons.SELL);
    @FXML
    public AnchorPane holderPane;
    @FXML
    public HBox topHBox;
    @FXML
    public TextField volume, amount, price;
    @FXML
    public TableView<OrderBookListItem> orderBookTable;
    @FXML
    public TableColumn<OrderBookListItem, String> priceColumn, amountColumn, volumeColumn;
    @FXML
    public Button createOfferButton;
    private NavigationController navigationController;
    private SortedList<OrderBookListItem> offerList;

    private AnimationTimer pollingTimer;
    @FXML
    private TableColumn<String, OrderBookListItem> directionColumn, countryColumn, bankAccountTypeColumn;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private OrderBookController(OrderBook orderBook, OrderBookFilter orderBookFilter, User user, MessageFacade messageFacade, WalletFacade walletFacade, Settings settings, Persistence persistence)
    {
        this.orderBook = orderBook;
        this.orderBookFilter = orderBookFilter;
        this.user = user;
        this.messageFacade = messageFacade;
        this.walletFacade = walletFacade;
        this.settings = settings;
        this.persistence = persistence;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        orderBook.init();

        // init table
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
        amount.textProperty().addListener((observable, oldValue, newValue) -> {
            orderBookFilter.setAmount(textInputToNumber(oldValue, newValue));
            updateVolume();
        });

        price.textProperty().addListener((observable, oldValue, newValue) -> {
            orderBookFilter.setPrice(textInputToNumber(oldValue, newValue));
            updateVolume();
        });

        orderBookFilter.getDirectionChangedProperty().addListener((observable, oldValue, newValue) -> applyOffers());

        user.getBankAccountChangedProperty().addListener((observable, oldValue, newValue) -> orderBook.loadOffers());

        createOfferButton.setOnAction(e -> createOffer());

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

    private boolean isRegistered()
    {
        return user.getAccountId() != null;
    }

    private boolean areSettingsValid()
    {
        return !settings.getAcceptedLanguageLocales().isEmpty() &&
                !settings.getAcceptedCountries().isEmpty() &&
                !settings.getAcceptedArbitrators().isEmpty() &&
                user.getCurrentBankAccount() != null;
    }

    private void showRegistrationDialog()
    {
        int selectedIndex = -1;
        if (areSettingsValid())
        {
            if (walletFacade.isRegistrationFeeBalanceNonZero())
            {
                if (walletFacade.isRegistrationFeeBalanceSufficient())
                {
                    if (walletFacade.isRegistrationFeeConfirmed())
                    {
                        selectedIndex = 2;
                    }
                    else
                    {
                        Action response = Popups.openErrorPopup("Registration fee not confirmed yet",
                                                                "The registration fee transaction has not been confirmed yet in the blockchain. Please wait until it has at least 1 confirmation.");
                        if (response == Dialog.Actions.OK)
                        {
                            MainController.GET_INSTANCE().navigateToView(NavigationItem.FUNDS);
                        }
                    }
                }
                else
                {
                    Action response = Popups.openErrorPopup("Missing registration fee",
                                                            "You have not funded the full registration fee of " + BitSquareFormatter.formatCoinToBtcWithCode(FeePolicy.ACCOUNT_REGISTRATION_FEE) + " BTC.");
                    if (response == Dialog.Actions.OK)
                    {
                        MainController.GET_INSTANCE().navigateToView(NavigationItem.FUNDS);
                    }
                }
            }
            else
            {
                selectedIndex = 1;
            }
        }
        else
        {
            selectedIndex = 0;
        }

        if (selectedIndex >= 0)
        {
            Dialogs.CommandLink settingsCommandLink = new Dialogs.CommandLink("Open settings", "You need to configure your settings before you can actively trade.");
            Dialogs.CommandLink depositFeeCommandLink = new Dialogs.CommandLink("Deposit funds",
                                                                                "You need to pay the registration fee before you can actively trade. That is needed as prevention against fraud.");
            Dialogs.CommandLink sendRegistrationCommandLink = new Dialogs.CommandLink("Publish registration",
                                                                                      "When settings are configured and the fee deposit is done your registration transaction will be published to "
                                                                                              + "the Bitcoin \nnetwork.");
            List<Dialogs.CommandLink> commandLinks = Arrays.asList(settingsCommandLink, depositFeeCommandLink, sendRegistrationCommandLink);
            Action registrationMissingAction = Popups.openRegistrationMissingPopup("Not registered yet",
                                                                                   "Please follow these steps:",
                                                                                   "You need to register before you can place an offer.",
                                                                                   commandLinks,
                                                                                   selectedIndex);
            if (registrationMissingAction == settingsCommandLink)
            {
                MainController.GET_INSTANCE().navigateToView(NavigationItem.SETTINGS);
            }
            else if (registrationMissingAction == depositFeeCommandLink)
            {
                MainController.GET_INSTANCE().navigateToView(NavigationItem.FUNDS);
            }
            else if (registrationMissingAction == sendRegistrationCommandLink)
            {
                payRegistrationFee();
            }
        }
    }

    private void payRegistrationFee()
    {
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(@javax.annotation.Nullable Transaction transaction)
            {
                log.debug("payRegistrationFee onSuccess");
                if (transaction != null)
                {
                    log.info("payRegistrationFee onSuccess tx id:" + transaction.getHashAsString());
                }
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.debug("payRegistrationFee onFailure");
            }
        };
        try
        {
            walletFacade.payRegistrationFee(user.getStringifiedBankAccounts(), callback);
            if (walletFacade.getRegistrationAddressInfo() != null)
            {
                user.setAccountID(walletFacade.getRegistrationAddressInfo().toString());
            }

            persistence.write(user.getClass().getName(), user);
        } catch (InsufficientMoneyException e1)
        {
            Popups.openInsufficientMoneyPopup();
        }
    }


    private void createOffer()
    {
        if (isRegistered())
        {
           /* if (walletFacade.isUnusedTradeAddressBalanceAboveCreationFee())
            { */
            ChildController nextController = navigationController.navigateToView(NavigationItem.CREATE_OFFER);
            if (nextController != null)
                ((CreateOfferController) nextController).setOrderBookFilter(orderBookFilter);
           /* }
            else
            {
                Action response = Popups.openErrorPopup("No funds for a trade", "You have to add some funds before you create a new offer.");
                if (response == Dialog.Actions.OK)
                    MainController.GET_INSTANCE().navigateToView(NavigationItem.FUNDS);
            }  */
        }
        else
        {
            showRegistrationDialog();
        }
    }

    private void takeOffer(Offer offer)
    {
        if (isRegistered())
        {
            TakerOfferController takerOfferController = (TakerOfferController) navigationController.navigateToView(NavigationItem.TAKE_OFFER);

            Coin requestedAmount;
            if (!"".equals(amount.getText()))
            {
                requestedAmount = BitSquareFormatter.parseBtcToCoin(amount.getText());
            }
            else
            {
                requestedAmount = offer.getAmount();
            }

            if (takerOfferController != null)
            {
                takerOfferController.initWithData(offer, requestedAmount);
            }
        }
        else
        {
            showRegistrationDialog();
        }
    }

    private void removeOffer(Offer offer)
    {
        orderBook.removeOffer(offer);
    }

    private void applyOffers()
    {
        orderBook.applyFilter(orderBookFilter);

        priceColumn.setSortType((orderBookFilter.getDirection() == Direction.BUY) ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);
        orderBookTable.sort();

        if (orderBookTable.getItems() != null)
        {
            createOfferButton.setDefaultButton(orderBookTable.getItems().isEmpty());
        }
    }

    private void setupPolling()
    {
        pollingTimer = Utilities.setInterval(1000, (animationTimer) -> {
            if (user.getCurrentBankAccount() != null)
            {
                messageFacade.getDirtyFlag(user.getCurrentBankAccount().getCurrency());
            }
            else
            {
                messageFacade.getDirtyFlag(CurrencyUtil.getDefaultCurrency());
            }
            return null;
        });

        messageFacade.getIsDirtyProperty().addListener((observableValue, oldValue, newValue) -> orderBook.loadOffers());
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

                            if (offer.getMessagePublicKey().equals(user.getMessagePublicKey()))
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
                                    title = BitSquareFormatter.formatDirection(Direction.BUY, true);
                                }
                                else
                                {
                                    icon = sellIcon;
                                    title = BitSquareFormatter.formatDirection(Direction.SELL, true);
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
                            Country country = orderBookListItem.getOffer().getBankAccountCountry();
                            try
                            {
                                hBox.getChildren().add(Icons.getIconImageView("/images/countries/" + country.getCode().toLowerCase() + ".png"));

                            } catch (Exception e)
                            {
                                log.warn("Country icon not found: " + "/images/countries/" + country.getCode().toLowerCase() + ".png country name: " + country.getName());
                            }
                            Tooltip.install(this, new Tooltip(country.getName()));
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
                            BankAccountType bankAccountType = orderBookListItem.getOffer().getBankAccountType();
                            setText(Localisation.get(bankAccountType.toString()));
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
        if (!"".equals(newValue))
        {
            try
            {
                DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.getDefault());
                d = decimalFormat.parse(newValue).doubleValue();
            } catch (ParseException e)
            {
                amount.setText(oldValue);
                d = BitSquareConverter.stringToDouble(oldValue);
            }
        }
        return d;
    }

    private void updateVolume()
    {
        double a = textInputToNumber(amount.getText(), amount.getText());
        double p = textInputToNumber(price.getText(), price.getText());
        volume.setText(BitSquareFormatter.formatPrice(a * p));
    }
}

