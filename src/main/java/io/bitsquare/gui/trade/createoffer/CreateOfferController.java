package io.bitsquare.gui.trade.createoffer;

import io.bitsquare.BitSquare;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.CachedViewController;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.ValidatingTextField;
import io.bitsquare.gui.components.btc.AddressTextField;
import io.bitsquare.gui.components.btc.BalanceTextField;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.trade.TradeController;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.gui.util.BtcValidator;
import io.bitsquare.gui.util.FiatValidator;
import io.bitsquare.gui.util.ValidationHelper;
import io.bitsquare.locale.Localisation;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;
import java.net.URL;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOfferController extends CachedViewController
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferController.class);


    private final TradeManager tradeManager;
    private final WalletFacade walletFacade;
    final ViewModel viewModel = new ViewModel();
    private final double collateral;
    private final String offerId;
    private Direction direction;
    private AddressEntry addressEntry;

    @FXML private AnchorPane rootContainer;
    @FXML private Label buyLabel, confirmationLabel, txTitleLabel, collateralLabel;

    @FXML private ValidatingTextField amountTextField, minAmountTextField, priceTextField, volumeTextField;
    @FXML private Button placeOfferButton, closeButton;
    @FXML private TextField totalsTextField, collateralTextField, bankAccountTypeTextField, bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField, acceptedLanguagesTextField,
            feeLabel, transactionIdTextField;
    @FXML private ConfidenceProgressIndicator progressIndicator;
    @FXML private AddressTextField addressTextField;
    @FXML private BalanceTextField balanceTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    CreateOfferController(TradeManager tradeManager, WalletFacade walletFacade, Settings settings, User user)
    {
        this.tradeManager = tradeManager;
        this.walletFacade = walletFacade;

        this.collateral = settings.getCollateral();

        viewModel.collateralLabel.set("Collateral (" + BitSquareFormatter.formatCollateralPercent(collateral) + "):");
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

        offerId = UUID.randomUUID().toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        super.initialize(url, rb);

        //TODO just for dev testing
        if (BitSquare.fillFormsWithDummyData)
        {
            amountTextField.setText("1.0");
            minAmountTextField.setText("0.1");
            priceTextField.setText("" + (int) (499 - new Random().nextDouble() * 1000 / 100));
        }

        setupBindings();
        setupValidation();

        //TODO
        if (walletFacade.getWallet() != null)
        {
            addressEntry = walletFacade.getAddressInfoByTradeID(offerId);
            addressTextField.setAddress(addressEntry.getAddress().toString());

            balanceTextField.setAddress(addressEntry.getAddress());
            balanceTextField.setWalletFacade(walletFacade);
        }
    }

    @Override
    public void deactivate()
    {
        super.deactivate();
        ((TradeController) parentController).onCreateOfferViewRemoved();
    }

    @Override
    public void activate()
    {
        super.activate();
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
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onPlaceOffer()
    {
        amountTextField.reValidate();
        minAmountTextField.reValidate();
        volumeTextField.reValidate();
        priceTextField.reValidate();

        //balanceTextField.getBalance()

        if (amountTextField.getIsValid() && minAmountTextField.getIsValid() && volumeTextField.getIsValid() && amountTextField.getIsValid())
        {
            viewModel.isPlaceOfferButtonDisabled.set(true);
           
            tradeManager.requestPlaceOffer(offerId,
                                           direction,
                                           BitSquareFormatter.parseToDouble(viewModel.price.get()),
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
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupBindings()
    {
        // TODO check that entered decimal places are nto exceeded supported

        viewModel.amount.addListener((ov, oldValue, newValue) -> {
            double amount = BitSquareFormatter.parseToDouble(newValue);
            double price = BitSquareFormatter.parseToDouble(viewModel.price.get());
            double volume = amount * price;
            viewModel.volume.set(BitSquareFormatter.formatVolume(volume));
            viewModel.totals.set(BitSquareFormatter.formatTotalsAsBtc(viewModel.amount.get(), collateral, FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE)));
            viewModel.collateral.set(BitSquareFormatter.formatCollateralAsBtc(viewModel.amount.get(), collateral));
        });

        viewModel.price.addListener((ov, oldValue, newValue) -> {
            double price = BitSquareFormatter.parseToDouble(newValue);
            double amount = BitSquareFormatter.parseToDouble(viewModel.amount.get());
            double volume = amount * price;
            viewModel.volume.set(BitSquareFormatter.formatVolume(volume));
        });

        viewModel.volume.addListener((ov, oldValue, newValue) -> {
            double volume = BitSquareFormatter.parseToDouble(newValue);
            double price = BitSquareFormatter.parseToDouble(viewModel.price.get());
            if (price != 0)
            {
                double amount = volume / price;
                viewModel.amount.set(BitSquareFormatter.formatVolume(amount));
                viewModel.totals.set(BitSquareFormatter.formatTotalsAsBtc(viewModel.amount.get(), collateral, FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE)));
                viewModel.collateral.set(BitSquareFormatter.formatCollateralAsBtc(viewModel.amount.get(), collateral));
            }
        });

        volumeTextField.focusedProperty().addListener((observableValue, oldValue, newValue) -> {
            if (oldValue && !newValue)
            {
                if (!volumeTextField.getText().equals(viewModel.volume.get()))
                {
                    Popups.openWarningPopup("Warning", "The total volume you have entered leads to invalid fractional Bitcoin amounts.\nThe amount has been adjusted and a new total volume be calculated from it.");
                    volumeTextField.setText(viewModel.volume.get());
                }
            }
        });

        buyLabel.textProperty().bind(viewModel.directionLabel);
        amountTextField.textProperty().bindBidirectional(viewModel.amount);
        priceTextField.textProperty().bindBidirectional(viewModel.price);
        volumeTextField.textProperty().bindBidirectional(viewModel.volume);

        minAmountTextField.textProperty().bindBidirectional(viewModel.minAmount);
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
        closeButton.visibleProperty().bind(viewModel.isOfferPlacedScreen);

        //TODO
       /* progressIndicator.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        confirmationLabel.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        txTitleLabel.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        transactionIdTextField.visibleProperty().bind(viewModel.isOfferPlacedScreen);
       */

        placeOfferButton.disableProperty().bind(amountTextField.isValidProperty()
                                                               .and(minAmountTextField.isValidProperty())
                                                               .and(volumeTextField.isValidProperty())
                                                               .and(priceTextField.isValidProperty()).not());
    }

    private void setupValidation()
    {
        BtcValidator amountValidator = new BtcValidator();
        amountTextField.setNumberValidator(amountValidator);
        amountTextField.setErrorPopupLayoutReference((Region) amountTextField.getParent());

        priceTextField.setNumberValidator(new FiatValidator());
        priceTextField.setErrorPopupLayoutReference((Region) amountTextField.getParent());

        BtcValidator volumeValidator = new BtcValidator();
        volumeTextField.setNumberValidator(volumeValidator);
        volumeTextField.setErrorPopupLayoutReference((Region) volumeTextField.getParent());

        BtcValidator minAmountValidator = new BtcValidator();
        minAmountTextField.setNumberValidator(minAmountValidator);

        ValidationHelper.setupMinAmountInRangeOfAmountValidation(amountTextField,
                                                                 minAmountTextField,
                                                                 viewModel.amount,
                                                                 viewModel.minAmount,
                                                                 amountValidator,
                                                                 minAmountValidator);

        amountTextField.focusedProperty().addListener((ov, oldValue, newValue) -> {
            // only on focus out and ignore focus loss from window
            if (!newValue && amountTextField.getScene() != null && amountTextField.getScene().getWindow().isFocused())
                volumeTextField.reValidate();
        });
        volumeTextField.focusedProperty().addListener((ov, oldValue, newValue) -> {
            // only on focus out and ignore focus loss from window
            if (!newValue && volumeTextField.getScene() != null && volumeTextField.getScene().getWindow().isFocused())
                amountTextField.reValidate();
        });
        priceTextField.focusedProperty().addListener((ov, oldValue, newValue) -> {
            // only on focus out and ignore focus loss from window
            if (!newValue && priceTextField.getScene() != null && priceTextField.getScene().getWindow().isFocused())
                volumeTextField.reValidate();
        });
    }
}

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
    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(false);
}