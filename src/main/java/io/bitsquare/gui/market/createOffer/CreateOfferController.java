package io.bitsquare.gui.market.createOffer;

import com.google.bitcoin.core.Coin;
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
 * Represents the visible state of he view
 */
class ViewModel
{
    StringProperty amount = new SimpleStringProperty();
    StringProperty minAmount = new SimpleStringProperty();
    StringProperty price = new SimpleStringProperty();
    StringProperty volume = new SimpleStringProperty();
    StringProperty collateral = new SimpleStringProperty();
    StringProperty totals = new SimpleStringProperty();
    StringProperty direction = new SimpleStringProperty();

    BooleanProperty isOfferPlacedScreen = new SimpleBooleanProperty();

}

public class CreateOfferController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferController.class);

    private final TradeManager tradeManager;
    private final WalletFacade walletFacade;
    private final Settings settings;
    private final User user;
    private final ViewModel viewModel = new ViewModel();
    private Direction direction;

    private NavigationController navigationController;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private Label buyLabel, confirmationLabel, txTitleLabel, collateralLabel;
    @FXML
    private TextField volumeTextField, amountTextField, priceTextField, totalsTextField;
    @FXML
    private Button placeOfferButton, closeButton;
    @FXML
    private TextField collateralTextField, minAmountTextField, bankAccountTypeTextField, bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField,
            acceptedLanguagesTextField, feeLabel, txTextField;
    @FXML
    private ConfidenceProgressIndicator progressIndicator;
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
        this.settings = settings;
        this.user = user;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOrderBookFilter(OrderBookFilter orderBookFilter)
    {
        direction = orderBookFilter.getDirection();

        viewModel.direction.set(BitSquareFormatter.formatDirection(direction, false));
        viewModel.amount.set(BitSquareFormatter.formatCoin(orderBookFilter.getAmount()));
        viewModel.minAmount.set(BitSquareFormatter.formatCoin(orderBookFilter.getAmount()));
        viewModel.price.set(BitSquareFormatter.formatPrice(orderBookFilter.getPrice()));

        buyLabel.setText(viewModel.direction.get() + ":");
        collateralLabel.setText("Collateral (" + BitSquareFormatter.formatCollateralPercent(settings.getCollateral()) + "):");

        //TODO just for dev testing
        if (BitSquare.fillFormsWithDummyData)
        {
            if (orderBookFilter.getAmount() != null)
            {
                amountTextField.setText("1");
                minAmountTextField.setText("0.1");
            }
            
            if (orderBookFilter.getPrice() != 0)
                priceTextField.setText("" + (int) (499 - new Random().nextDouble() * 1000 / 100));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        // static data
        BankAccount currentBankAccount = user.getCurrentBankAccount();
        if (currentBankAccount != null)
        {
            bankAccountTypeTextField.setText(Localisation.get(currentBankAccount.getBankAccountType().toString()));
            bankAccountCurrencyTextField.setText(currentBankAccount.getCurrency().getCurrencyCode());
            bankAccountCountyTextField.setText(currentBankAccount.getCountry().getName());
        }
        acceptedCountriesTextField.setText(BitSquareFormatter.countryLocalesToString(settings.getAcceptedCountries()));
        acceptedLanguagesTextField.setText(BitSquareFormatter.languageLocalesToString(settings.getAcceptedLanguageLocales()));

        feeLabel.setText(BitSquareFormatter.formatCoinWithCode(FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE)));

        AddressEntry addressEntry = walletFacade.getUnusedTradeAddressInfo();
        addressTextField.setAddress(addressEntry.getAddress().toString());

        balanceTextField.setAddress(addressEntry.getAddress());
        balanceTextField.setWalletFacade(walletFacade);

        // setup bindings
        DoubleBinding amountBinding = createDoubleBinding(() -> BitSquareConverter.stringToDouble(viewModel.amount.get()), viewModel.amount);
        DoubleBinding priceBinding = createDoubleBinding(() -> BitSquareConverter.stringToDouble(viewModel.price.get()), viewModel.price);
        viewModel.volume.bind(createStringBinding(() -> BitSquareFormatter.formatVolume(amountBinding.get() * priceBinding.get()), amountBinding, priceBinding));

        viewModel.collateral.bind(createStringBinding(() -> {
            Coin amountAsCoin = BitSquareFormatter.parseToCoin(viewModel.amount.get());
            Coin collateralAsCoin = amountAsCoin.divide((long) (1d / settings.getCollateral()));
            return BitSquareFormatter.formatCoinWithCode(collateralAsCoin);
        }, amountBinding));

        viewModel.totals.bind(createStringBinding(() -> {
            Coin amountAsCoin = BitSquareFormatter.parseToCoin(viewModel.amount.get());
            Coin collateralAsCoin = amountAsCoin.divide((long) (1d / settings.getCollateral()));
            Coin totals = FeePolicy.CREATE_OFFER_FEE.add(collateralAsCoin).add(FeePolicy.TX_FEE);
            return BitSquareFormatter.formatCoinWithCode(totals);
        }, amountBinding, priceBinding));

        // apply bindings to controls
        amountTextField.textProperty().bindBidirectional(viewModel.amount);
        priceTextField.textProperty().bindBidirectional(viewModel.price);
        minAmountTextField.textProperty().bindBidirectional(viewModel.minAmount);

        volumeTextField.textProperty().bind(viewModel.volume);
        collateralTextField.textProperty().bind(viewModel.collateral);
        totalsTextField.textProperty().bind(viewModel.totals);

        placeOfferButton.visibleProperty().bind(viewModel.isOfferPlacedScreen.not());
        progressIndicator.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        confirmationLabel.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        txTitleLabel.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        txTextField.visibleProperty().bind(viewModel.isOfferPlacedScreen);
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
            placeOfferButton.setDisable(true);

            double price = BitSquareConverter.stringToDouble(viewModel.price.get());
            Coin amount = BitSquareFormatter.parseToCoin(viewModel.amount.get());
            Coin minAmount = BitSquareFormatter.parseToCoin(viewModel.minAmount.get());

            tradeManager.requestPlaceOffer(direction,
                                           price,
                                           amount,
                                           minAmount,
                                           (transaction) -> {
                                               viewModel.isOfferPlacedScreen.set(true);
                                               txTextField.setText(transaction.getHashAsString());
                                           },
                                           errorMessage -> {
                                               Popups.openErrorPopup("An error occurred", errorMessage);
                                               placeOfferButton.setDisable(false);
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
        boolean inputFieldsValid;
        double amount = BitSquareConverter.stringToDouble(viewModel.amount.get());
        double minAmount = BitSquareConverter.stringToDouble(viewModel.minAmount.get());
        double price = BitSquareConverter.stringToDouble(viewModel.price.get());

        inputFieldsValid = price > 0 &&
                amount > 0 &&
                minAmount > 0 &&
                minAmount <= amount/* &&
                viewModel.collateral >= settings.getMinCollateral() &&
                viewModel.collateral <= settings.getMaxCollateral()*/;

        if (!inputFieldsValid)
        {
            Popups.openWarningPopup("Invalid input", "Your input is invalid");
            return false;
        }

       /* Arbitrator arbitrator = settings.getRandomArbitrator(getAmountAsCoin());
        if (arbitrator == null)
        {
            Popups.openWarningPopup("No arbitrator available", "No arbitrator from your arbitrator list does match the collateral and amount value.");
            return false;
        }*/

        if (user.getCurrentBankAccount() == null)
        {
            log.error("Must never happen!");
            Popups.openWarningPopup("No bank account selected", "No bank account selected.");
            return false;
        }

        return true;
    }
}

