package io.bitsquare.gui.market.createOffer;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
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
import io.bitsquare.gui.util.BitSquareValidator;
import io.bitsquare.locale.Localisation;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trading;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.User;
import java.io.IOException;
import java.net.URL;
import java.util.Random;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOfferController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferController.class);

    private final Trading trading;
    private final WalletFacade walletFacade;
    private final Settings settings;
    private final User user;


    private NavigationController navigationController;
    private Direction direction;
    private Offer offer;
    private AddressEntry addressEntry;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private Label buyLabel, placeOfferTitle, confirmationLabel, txTitleLabel, collateralLabel;
    @FXML
    private TextField volumeTextField, amountTextField, priceTextField, totalTextField;
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
    private CreateOfferController(Trading trading, WalletFacade walletFacade, Settings settings, User user)
    {
        this.trading = trading;
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
        //TODO
        amountTextField.setText(BitSquareFormatter.formatPrice(orderBookFilter.getAmount()));
        //TODO
        minAmountTextField.setText(BitSquareFormatter.formatPrice(orderBookFilter.getAmount()));

        priceTextField.setText(BitSquareFormatter.formatPrice(orderBookFilter.getPrice()));
        buyLabel.setText(BitSquareFormatter.formatDirection(direction, false) + ":");
        collateralLabel.setText("Collateral (" + getCollateralAsPercent() + "):");

        updateVolume();
        updateTotals();

        //TODO
        if (BitSquare.fillFormsWithDummyData)
        {
            //amountTextField.setText("" + (int) (new Random().nextDouble() * 100 / 10 + 1));
            amountTextField.setText("1");
            priceTextField.setText("" + (int) (499 - new Random().nextDouble() * 1000 / 100));
            minAmountTextField.setText("0.1");
        }

        updateVolume();
        updateTotals();
        applyCollateral();

        amountTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateVolume();
            updateTotals();
            applyCollateral();
        });

        priceTextField.textProperty().addListener((observable, oldValue, newValue) -> updateVolume());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        BankAccount currentBankAccount = user.getCurrentBankAccount();
        if (currentBankAccount != null)
        {
            bankAccountTypeTextField.setText(Localisation.get(currentBankAccount.getBankAccountType().toString()));
            bankAccountCurrencyTextField.setText(currentBankAccount.getCurrency().getCurrencyCode());
            bankAccountCountyTextField.setText(currentBankAccount.getCountry().getName());
        }
        acceptedCountriesTextField.setText(BitSquareFormatter.countryLocalesToString(settings.getAcceptedCountries()));
        acceptedLanguagesTextField.setText(BitSquareFormatter.languageLocalesToString(settings.getAcceptedLanguageLocales()));
        feeLabel.setText(BitSquareFormatter.formatCoinToBtcWithCode(FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE)));

        addressEntry = walletFacade.getUnusedTradeAddressInfo();
        addressTextField.setAddress(addressEntry.getAddress().toString());

        balanceTextField.setAddress(addressEntry.getAddress());
        balanceTextField.setWalletFacade(walletFacade);
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

    public void onPlaceOffer()
    {
        if (!inputValid())
        {
            Popups.openWarningPopup("Invalid input", "Your input is invalid");
            return;
        }

        //TODO will be derived form arbitrators
        double collateral = getCollateral();
        Arbitrator arbitrator = settings.getRandomArbitrator(collateral, getAmountAsCoin());
        if (arbitrator == null)
        {
            Popups.openWarningPopup("No arbitrator available", "No arbitrator from your arbitrator list does match the collateral and amount value.");
            return;
        }

        if (user.getCurrentBankAccount() != null)
        {
            Coin amountAsCoin = BitSquareFormatter.parseBtcToCoin(getAmountString());
            Coin minAmountAsCoin = BitSquareFormatter.parseBtcToCoin(getMinAmountString());

            offer = new Offer(user.getMessagePublicKey(),
                              direction,
                              BitSquareConverter.stringToDouble(priceTextField.getText()),
                              amountAsCoin,
                              minAmountAsCoin,
                              user.getCurrentBankAccount().getBankAccountType(),
                              user.getCurrentBankAccount().getCurrency(),
                              user.getCurrentBankAccount().getCountry(),
                              user.getCurrentBankAccount().getUid(),
                              arbitrator,
                              collateral,
                              settings.getAcceptedCountries(),
                              settings.getAcceptedLanguageLocales());

            addressEntry.setTradeId(offer.getId());

            try
            {
                walletFacade.payCreateOfferFee(offer.getId(), new FutureCallback<Transaction>()
                {
                    @Override
                    public void onSuccess(@javax.annotation.Nullable Transaction transaction)
                    {
                        log.info("sendResult onSuccess:" + transaction);
                        if (transaction != null)
                        {
                            offer.setOfferFeePaymentTxID(transaction.getHashAsString());
                            setupSuccessScreen(transaction);

                            //  placeOfferTitle.setText("Transaction sent:");
                            try
                            {
                                trading.addOffer(offer);
                            } catch (IOException e)
                            {
                                Popups.openErrorPopup("Error on adding offer", "Could not add offer to orderbook. " + e.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t)
                    {
                        log.warn("sendResult onFailure:" + t);
                        Popups.openErrorPopup("Fee payment failed", "Fee payment failed. " + t);
                        placeOfferButton.setDisable(false);
                    }
                });
                placeOfferButton.setDisable(true);
            } catch (InsufficientMoneyException e1)
            {
                Popups.openInsufficientMoneyPopup();
            }
        }
    }


    public void onClose()
    {
        TabPane tabPane = ((TabPane) (rootContainer.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

        navigationController.navigateToView(NavigationItem.ORDER_BOOK);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupSuccessScreen(Transaction newTransaction)
    {
        placeOfferButton.setVisible(false);

        progressIndicator.setVisible(true);
        confirmationLabel.setVisible(true);
        txTitleLabel.setVisible(true);
        txTextField.setVisible(true);
        closeButton.setVisible(true);

        txTextField.setText(newTransaction.getHashAsString());
    }

    private void updateTotals()
    {
        Coin amountAsCoin = BitSquareFormatter.parseBtcToCoin(getAmountString());
        Coin collateral = amountAsCoin.divide((long) (1d / getCollateral()));
        Coin totals = FeePolicy.CREATE_OFFER_FEE.add(collateral).add(FeePolicy.TX_FEE);
        totalTextField.setText(BitSquareFormatter.formatCoinToBtcWithCode(totals));
    }

    private void updateVolume()
    {
        volumeTextField.setText(BitSquareFormatter.formatVolume(getVolume()));
    }

    private double getVolume()
    {
        double amountAsDouble = BitSquareConverter.stringToDouble(getAmountString());
        double priceAsDouble = BitSquareConverter.stringToDouble(priceTextField.getText());
        return amountAsDouble * priceAsDouble;
    }

    private void applyCollateral()
    {
        collateralTextField.setText(getFormattedCollateralAsBtc());
    }

    private String getFormattedCollateralAsBtc()
    {
        Coin amountAsCoin = BitSquareFormatter.parseBtcToCoin(getAmountString());
        Coin collateralAsCoin = amountAsCoin.divide((long) (1d / getCollateral()));
        return BitSquareFormatter.formatCoinToBtc(collateralAsCoin);
    }

    private String getCollateralAsPercent()
    {
        return BitSquareFormatter.formatCollateralPercent(getCollateral());
    }


    private Coin getAmountAsCoin()
    {
        return BitSquareFormatter.parseBtcToCoin(getAmountString());
    }

    private String getAmountString()
    {
        try
        {
            BitSquareValidator.textFieldsHasPositiveDoubleValueWithReset(amountTextField);
            return amountTextField.getText();
        } catch (BitSquareValidator.ValidationException e)
        {
            return "0";
        }
    }

    private String getMinAmountString()
    {
        try
        {
            BitSquareValidator.textFieldsHasPositiveDoubleValueWithReset(minAmountTextField);
            return minAmountTextField.getText();
        } catch (BitSquareValidator.ValidationException e)
        {
            return "0";
        }
    }

    private double getCollateral()
    {
        // TODO
        return settings.getCollateral();
    }

    private boolean inputValid()
    {
        double priceAsDouble = BitSquareConverter.stringToDouble(priceTextField.getText());
        double minAmountAsDouble = BitSquareConverter.stringToDouble(minAmountTextField.getText());
        double amountAsDouble = BitSquareConverter.stringToDouble(getAmountString());
        double collateralAsDouble = BitSquareConverter.stringToDouble(collateralTextField.getText());

        return priceAsDouble > 0 &&
                amountAsDouble > 0 &&
                minAmountAsDouble > 0 &&
                minAmountAsDouble <= amountAsDouble/* &&
                collateralAsDouble >= settings.getMinCollateral() &&
                collateralAsDouble <= settings.getMaxCollateral()*/;
    }

}

