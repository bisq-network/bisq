package io.bitsquare.gui.market.createOffer;

import io.bitsquare.BitSquare;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.components.btc.AddressTextField;
import io.bitsquare.gui.components.btc.BalanceTextField;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.popups.Popups;
import io.bitsquare.gui.util.BitSquareConverter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.locale.Localisation;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;
import java.net.URL;
import java.util.Random;
import java.util.ResourceBundle;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.beans.binding.Bindings.createDoubleBinding;
import static javafx.beans.binding.Bindings.createStringBinding;

/**
 * Represents the visible state of the view
 */
class ViewModel
{
    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty collateral = new SimpleStringProperty();
    final StringProperty totals = new SimpleStringProperty();
    final StringProperty directionLabel = new SimpleStringProperty();
    final StringProperty collateralLabel = new SimpleStringProperty();
    final StringProperty feeLabel = new SimpleStringProperty();
    final StringProperty bankAccountType = new SimpleStringProperty();
    final StringProperty bankAccountCurrency = new SimpleStringProperty();
    final StringProperty bankAccountCounty = new SimpleStringProperty();
    final StringProperty acceptedCountries = new SimpleStringProperty();
    final StringProperty acceptedLanguages = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final BooleanProperty isOfferPlacedScreen = new SimpleBooleanProperty();
    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty();
}

public class CreateOfferController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferController.class);

    private NavigationController navigationController;
    private final TradeManager tradeManager;
    private final WalletFacade walletFacade;
    private final ViewModel viewModel = new ViewModel();
    private final double collateral;
    private Direction direction;

    @FXML private AnchorPane rootContainer;
    @FXML private Label buyLabel, confirmationLabel, txTitleLabel, collateralLabel;
    @FXML private TextField volumeTextField, amountTextField, priceTextField, totalsTextField;
    @FXML private Button placeOfferButton, closeButton;
    @FXML private TextField collateralTextField, minAmountTextField, bankAccountTypeTextField, bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField, acceptedLanguagesTextField, 
            feeLabel, transactionIdTextField;
    @FXML private ConfidenceProgressIndicator progressIndicator;
    @FXML private AddressTextField addressTextField;
    @FXML private BalanceTextField balanceTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateOfferController(TradeManager tradeManager, WalletFacade walletFacade, Settings settings, User user)
    {
        this.tradeManager = tradeManager;
        this.walletFacade = walletFacade;

        this.collateral = settings.getCollateral();

        BankAccount bankAccount = user.getCurrentBankAccount();
        if (bankAccount != null)
        {
            viewModel.bankAccountType.set(Localisation.get(bankAccount.getBankAccountType().toString()));
            viewModel.bankAccountCurrency.set(bankAccount.getCurrency().getCurrencyCode());
            viewModel.bankAccountCounty.set(bankAccount.getCountry().getName());
        }
        viewModel.acceptedCountries.set(BitSquareFormatter.countryLocalesToString(settings.getAcceptedCountries()));
        viewModel.acceptedLanguages.set(BitSquareFormatter.languageLocalesToString(settings.getAcceptedLanguageLocales()));
        viewModel.feeLabel.set(BitSquareFormatter.formatCoinWithCode(FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOrderBookFilter(OrderBookFilter orderBookFilter)
    {
        direction = orderBookFilter.getDirection();

        viewModel.directionLabel.set(BitSquareFormatter.formatDirection(direction, false) + ":");
        viewModel.amount.set(BitSquareFormatter.formatCoin(orderBookFilter.getAmount()));
        viewModel.minAmount.set(BitSquareFormatter.formatCoin(orderBookFilter.getAmount()));
        viewModel.price.set(BitSquareFormatter.formatPrice(orderBookFilter.getPrice()));
        viewModel.collateralLabel.set("Collateral (" + BitSquareFormatter.formatCollateralPercent(collateral) + "):");

        //TODO just for dev testing
        if (BitSquare.fillFormsWithDummyData)
        {
            if (orderBookFilter.getAmount() == null)
            {
                amountTextField.setText("1");
                minAmountTextField.setText("0.1");
            }

            if (orderBookFilter.getPrice() == 0)
                priceTextField.setText("" + (int) (499 - new Random().nextDouble() * 1000 / 100));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        AddressEntry addressEntry = walletFacade.getUnusedTradeAddressInfo();
        addressTextField.setAddress(addressEntry.getAddress().toString());

        balanceTextField.setAddress(addressEntry.getAddress());
        balanceTextField.setWalletFacade(walletFacade);

        // setup bindings
        DoubleBinding amountBinding = createDoubleBinding(() -> BitSquareConverter.stringToDouble(viewModel.amount.get()), viewModel.amount);
        DoubleBinding priceBinding = createDoubleBinding(() -> BitSquareConverter.stringToDouble(viewModel.price.get()), viewModel.price);

        viewModel.volume.bind(createStringBinding(() -> BitSquareFormatter.formatVolume(amountBinding.get() * priceBinding.get()), amountBinding, priceBinding));
        viewModel.collateral.bind(createStringBinding(() -> BitSquareFormatter.formatCollateralAsBtc(viewModel.amount.get(), collateral), amountBinding));
        viewModel.totals.bind(createStringBinding(() -> BitSquareFormatter.formatTotalsAsBtc(viewModel.amount.get(), collateral, FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE)), amountBinding, priceBinding));

        // apply bindings to controls
        buyLabel.textProperty().bind(viewModel.directionLabel);
        amountTextField.textProperty().bindBidirectional(viewModel.amount);
        priceTextField.textProperty().bindBidirectional(viewModel.price);
        minAmountTextField.textProperty().bindBidirectional(viewModel.minAmount);

        volumeTextField.textProperty().bind(viewModel.volume);
        collateralLabel.textProperty().bind(viewModel.collateralLabel);
        collateralTextField.textProperty().bind(viewModel.collateral);
        totalsTextField.textProperty().bind(viewModel.totals);

        bankAccountTypeTextField.textProperty().bind(viewModel.bankAccountType);
        bankAccountCurrencyTextField.textProperty().bind(viewModel.bankAccountCurrency);
        bankAccountCountyTextField.textProperty().bind(viewModel.bankAccountCounty);

        acceptedCountriesTextField.textProperty().bind(viewModel.acceptedCountries);
        acceptedLanguagesTextField.textProperty().bind(viewModel.acceptedLanguages);
        feeLabel.textProperty().bind(viewModel.feeLabel);
        transactionIdTextField.textProperty().bind(viewModel.transactionId);

        placeOfferButton.visibleProperty().bind(viewModel.isOfferPlacedScreen.not());
        placeOfferButton.disableProperty().bind(viewModel.isPlaceOfferButtonDisabled);
        progressIndicator.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        confirmationLabel.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        txTitleLabel.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        transactionIdTextField.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        closeButton.visibleProperty().bind(viewModel.isOfferPlacedScreen);
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
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Hibernate
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sleep()
    {
        cleanup();
    }

    @Override
    public void awake()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////
    
    @FXML
    public void onPlaceOffer()
    {
        if (inputsValid())
        {
            viewModel.isPlaceOfferButtonDisabled.set(true);

            tradeManager.requestPlaceOffer(direction,
                                           BitSquareConverter.stringToDouble(viewModel.price.get()),
                                           BitSquareFormatter.parseToCoin(viewModel.amount.get()),
                                           BitSquareFormatter.parseToCoin(viewModel.minAmount.get()),
                                           (transaction) -> {
                                               viewModel.isOfferPlacedScreen.set(true);
                                               viewModel.transactionId.set(transaction.getHashAsString());
                                           },
                                           errorMessage -> {
                                               Popups.openErrorPopup("An error occurred", errorMessage);
                                               viewModel.isPlaceOfferButtonDisabled.set(false);
                                           });
        }
    }

    @FXML
    public void onClose()
    {
        TabPane tabPane = ((TabPane) (rootContainer.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

        navigationController.navigateToView(NavigationItem.ORDER_BOOK);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean inputsValid()
    {
        //TODO
       /* boolean inputFieldsValid;
        double amount = BitSquareConverter.stringToDouble(viewModel.amount.get());
        double minAmount = BitSquareConverter.stringToDouble(viewModel.minAmount.get());
        double price = BitSquareConverter.stringToDouble(viewModel.price.get());

        inputFieldsValid = price > 0 &&
                amount > 0 &&
                minAmount > 0 &&
                minAmount <= amount/* &&
                viewModel.collateral >= settings.getMinCollateral() &&
                viewModel.collateral <= settings.getMaxCollateral()*/ /*;

        if (!inputFieldsValid)
        {
            Popups.openWarningPopup("Invalid input", "Your input is invalid");
            return false;
        }
*/
       /* Arbitrator arbitrator = settings.getRandomArbitrator(getAmountAsCoin());
        if (arbitrator == null)
        {
            Popups.openWarningPopup("No arbitrator available", "No arbitrator from your arbitrator list does match the collateral and amount value.");
            return false;
        }*/
/*
        if (user.getCurrentBankAccount() == null)
        {
            log.error("Must never happen!");
            Popups.openWarningPopup("No bank account selected", "No bank account selected.");
            return false;
        }*/

        return true;
    }
}

