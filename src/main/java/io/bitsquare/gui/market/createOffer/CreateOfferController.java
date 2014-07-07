package io.bitsquare.gui.market.createOffer;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BtcFormatter;
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
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trading;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.User;
import java.io.IOException;
import java.math.BigInteger;
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
    private Label buyLabel, placeOfferTitle, confirmationLabel, txTitleLabel;
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
        amountTextField.setText(BitSquareFormatter.formatPrice(orderBookFilter.getAmount()));
        minAmountTextField.setText(BitSquareFormatter.formatPrice(orderBookFilter.getAmount()));
        priceTextField.setText(BitSquareFormatter.formatPrice(orderBookFilter.getPrice()));
        buyLabel.setText(BitSquareFormatter.formatDirection(direction, false) + ":");
        collateralTextField.setText(BitSquareFormatter.formatVolume(settings.getMinCollateral()));
        updateVolume();
        updateTotals();

        //TODO
        //amountTextField.setText("" + (int) (new Random().nextDouble() * 100 / 10 + 1));
        amountTextField.setText("1");
        priceTextField.setText("" + (int) (499 - new Random().nextDouble() * 1000 / 100));
        minAmountTextField.setText("0,1");
        collateralTextField.setText("10");
        updateVolume();
        updateTotals();

        amountTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateVolume();
            updateTotals();
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
        feeLabel.setText(BtcFormatter.formatSatoshis(FeePolicy.CREATE_OFFER_FEE));

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

        int collateral = (int) (BitSquareConverter.stringToDouble(collateralTextField.getText()));
        Arbitrator arbitrator = settings.getRandomArbitrator(collateral, getAmountAsBI());
        if (arbitrator == null)
        {
            Popups.openWarningPopup("No arbitrator available", "No arbitrator from your arbitrator list does match the collateral and amount value.");
            return;
        }

        log.debug("create offer pubkey " + user.getMessagePubKeyAsHex());

        if (user.getCurrentBankAccount() != null)
        {
            offer = new Offer(user.getMessagePubKeyAsHex(),
                              direction,
                              BitSquareConverter.stringToDouble(priceTextField.getText()),
                              BtcFormatter.stringValueToSatoshis(amountTextField.getText()),
                              BtcFormatter.stringValueToSatoshis(minAmountTextField.getText()),
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
        double amountAsDouble = BitSquareConverter.stringToDouble(amountTextField.getText());
        double collateralPercentAsDouble = BitSquareConverter.stringToDouble(collateralTextField.getText());
        double collateralAmountAsDouble = collateralPercentAsDouble * amountAsDouble / 100;
        BigInteger collateral = BtcFormatter.doubleValueToSatoshis(collateralAmountAsDouble);
        BigInteger totals = FeePolicy.CREATE_OFFER_FEE.add(collateral);
        totalTextField.setText(BtcFormatter.formatSatoshis(totals));
    }

    private void updateVolume()
    {
        volumeTextField.setText(BitSquareFormatter.formatVolume(getVolume()));
    }

    private double getVolume()
    {
        double amountAsDouble = BitSquareConverter.stringToDouble(amountTextField.getText());
        double priceAsDouble = BitSquareConverter.stringToDouble(priceTextField.getText());
        return amountAsDouble * priceAsDouble;
    }

    private BigInteger getAmountAsBI()
    {
        return BtcFormatter.stringValueToSatoshis(amountTextField.getText());
    }

    //TODO
    @SuppressWarnings("UnusedAssignment")
    private boolean inputValid()
    {
        double priceAsDouble = BitSquareConverter.stringToDouble(priceTextField.getText());
        double minAmountAsDouble = BitSquareConverter.stringToDouble(minAmountTextField.getText());
        double amountAsDouble = BitSquareConverter.stringToDouble(amountTextField.getText());
        double collateralAsDouble = BitSquareConverter.stringToDouble(collateralTextField.getText());

        return priceAsDouble > 0 &&
                amountAsDouble > 0 &&
                minAmountAsDouble > 0 &&
                minAmountAsDouble <= amountAsDouble/* &&
                collateralAsDouble >= settings.getMinCollateral() &&
                collateralAsDouble <= settings.getMaxCollateral()*/;
    }

}

