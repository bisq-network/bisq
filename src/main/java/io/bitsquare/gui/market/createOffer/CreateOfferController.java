package io.bitsquare.gui.market.createOffer;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.Fees;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.util.*;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trading;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.User;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.ResourceBundle;

public class CreateOfferController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferController.class);

    private NavigationController navigationController;
    private Trading trading;
    private WalletFacade walletFacade;
    private MessageFacade messageFacade;
    private Settings settings;
    private User user;
    private Direction direction;
    private Offer offer;
    private ConfidenceDisplay confidenceDisplay;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private Label buyLabel, placeOfferTitle, confirmationLabel, txTitleLabel;
    @FXML
    private TextField volumeTextField, amountTextField, priceTextField;
    @FXML
    private Button placeOfferButton, closeButton;
    @FXML
    private TextField collateralTextField, minAmountTextField, bankAccountTypeTextField, bankAccountCurrencyTextField, bankAccountCountyTextField,
            acceptedCountriesTextField, acceptedLanguagesTextField, feeLabel, txTextField;
    @FXML
    private ProgressIndicator progressIndicator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CreateOfferController(Trading trading, WalletFacade walletFacade, MessageFacade messageFacade, Settings settings, User user)
    {
        this.trading = trading;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.settings = settings;
        this.user = user;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOrderBookFilter(OrderBookFilter orderBookFilter)
    {
        direction = orderBookFilter.getDirection();
        amountTextField.setText(Formatter.formatPrice(orderBookFilter.getAmount()));
        minAmountTextField.setText(Formatter.formatPrice(orderBookFilter.getAmount()));
        priceTextField.setText(Formatter.formatPrice(orderBookFilter.getPrice()));
        buyLabel.setText(Formatter.formatDirection(direction, false) + ":");
        collateralTextField.setText(Formatter.formatVolume(settings.getMinCollateral()));
        updateVolume();


        //TODO
        amountTextField.setText("0,001");
        minAmountTextField.setText("0,001");
        priceTextField.setText("300");
        collateralTextField.setText("50");
        updateVolume();

        amountTextField.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                updateVolume();
            }
        });

        priceTextField.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                updateVolume();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        bankAccountTypeTextField.setText(Localisation.get(user.getCurrentBankAccount().getBankAccountType().getType().toString()));
        bankAccountCurrencyTextField.setText(user.getCurrentBankAccount().getCurrency().getCurrencyCode());
        bankAccountCountyTextField.setText(user.getCurrentBankAccount().getCountryLocale().getDisplayCountry());
        acceptedCountriesTextField.setText(Formatter.countryLocalesToString(settings.getAcceptedCountryLocales()));
        acceptedLanguagesTextField.setText(Formatter.languageLocalesToString(settings.getAcceptedLanguageLocales()));
        feeLabel.setText(Utils.bitcoinValueToFriendlyString(Fees.OFFER_CREATION_FEE));
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
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPlaceOffer(ActionEvent actionEvent)
    {
        if (!inputValid())
        {
            Popups.openWarningPopup("Invalid input", "Your input is invalid");
            return;
        }

        int collateral = (int) (Converter.stringToDouble(collateralTextField.getText()));
        Arbitrator arbitrator = settings.getRandomArbitrator(collateral, getAmountAsBI());
        if (arbitrator == null)
        {
            Popups.openWarningPopup("No arbitrator available", "No arbitrator from your arbitrator list does match the collateral and amount value.");
            return;
        }

        log.debug("create offer pubkey " + user.getMessagePubKeyAsHex());

        offer = new Offer(user.getMessagePubKeyAsHex(),
                direction,
                Converter.stringToDouble(priceTextField.getText()),
                BtcFormatter.stringValueToSatoshis(amountTextField.getText()),
                BtcFormatter.stringValueToSatoshis(minAmountTextField.getText()),
                user.getCurrentBankAccount().getBankAccountType().getType(),
                user.getCurrentBankAccount().getCurrency(),
                user.getCurrentBankAccount().getCountryLocale(),
                user.getCurrentBankAccount().getUid(),
                arbitrator,
                collateral,
                settings.getAcceptedCountryLocales(),
                settings.getAcceptedLanguageLocales());

        FutureCallback callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(Transaction transaction)
            {
                log.info("sendResult onSuccess:" + transaction.toString());
                offer.setOfferFeePaymentTxID(transaction.getHashAsString());
                setupSuccessScreen(transaction);
                placeOfferTitle.setText("Transaction sent:");

                try
                {
                    log.info("send offer to P2P orderbook");
                    messageFacade.addOffer(offer);
                } catch (IOException e)
                {
                    Popups.openErrorPopup("Could not publish offer", "Could not publish offer. " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.warn("sendResult onFailure:" + t.toString());
                Popups.openErrorPopup("Fee payment failed", "Fee payment failed. " + t.toString());
                placeOfferButton.setDisable(false);
            }
        };
        try
        {
            trading.placeNewOffer(offer, callback);
            placeOfferButton.setDisable(true);
        } catch (InsufficientMoneyException e1)
        {
            Popups.openErrorPopup("Not enough money available", "There is not enough money available. Please pay in first to your wallet. " + e1.getMessage());
        }
    }

    public void onClose(ActionEvent actionEvent)
    {
        TabPane tabPane = ((TabPane) (rootContainer.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

        navigationController.navigateToView(NavigationController.ORDER_BOOK, "Orderbook");
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

        confidenceDisplay = new ConfidenceDisplay(walletFacade.getWallet(), confirmationLabel, newTransaction, progressIndicator);
    }

    private void updateVolume()
    {
        volumeTextField.setText(Formatter.formatVolume(getVolume()));
    }

    private double getVolume()
    {
        double amountAsDouble = Converter.stringToDouble(amountTextField.getText());
        double priceAsDouble = Converter.stringToDouble(priceTextField.getText());
        return amountAsDouble * priceAsDouble;
    }

    private BigInteger getAmountAsBI()
    {
        return BtcFormatter.stringValueToSatoshis(amountTextField.getText());
    }

    private boolean inputValid()
    {
        double priceAsDouble = Converter.stringToDouble(priceTextField.getText());
        double minAmountAsDouble = Converter.stringToDouble(minAmountTextField.getText());
        double amountAsDouble = Converter.stringToDouble(amountTextField.getText());
        double collateralAsDouble = Converter.stringToDouble(collateralTextField.getText());

        return priceAsDouble > 0 &&
                amountAsDouble > 0 &&
                minAmountAsDouble > 0 &&
                minAmountAsDouble <= amountAsDouble/* &&
                collateralAsDouble >= settings.getMinCollateral() &&
                collateralAsDouble <= settings.getMaxCollateral()*/;
    }

}

