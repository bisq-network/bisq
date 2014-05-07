package io.bitsquare.gui.market.offer;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.Fees;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.components.ConfirmationComponent;
import io.bitsquare.gui.util.*;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trading;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.User;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URL;
import java.util.ResourceBundle;

public class CreateOfferController implements Initializable, ChildController, WalletFacade.WalletListener
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferController.class);

    private NavigationController navigationController;
    private Trading trading;
    private WalletFacade walletFacade;
    private Settings settings;
    private User user;
    private Direction direction;
    private Offer offer;
    private int gridRow;

    private Button placeOfferButton;
    private TextField collateralTextField, minAmountTextField;

    @FXML
    private AnchorPane holderPane;
    @FXML
    private GridPane formGridPane;
    @FXML
    public Label buyLabel;
    @FXML
    public TextField volumeTextField, amountTextField, priceTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CreateOfferController(Trading trading, WalletFacade walletFacade, Settings settings, User user)
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
        amountTextField.setText(Formatter.formatPrice(orderBookFilter.getAmount()));
        minAmountTextField.setText(Formatter.formatPrice(orderBookFilter.getAmount()));
        priceTextField.setText(Formatter.formatPrice(orderBookFilter.getPrice()));
        buyLabel.setText(Formatter.formatDirection(direction, false) + ":");
        collateralTextField.setText(Formatter.formatVolume(settings.getMinCollateral()));
        updateVolume();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        walletFacade.addRegistrationWalletListener(this);

        buildScreen();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: WalletFacade.WalletListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConfidenceChanged(int numBroadcastPeers, int depthInBlocks)
    {
        log.info("onConfidenceChanged " + numBroadcastPeers + " / " + depthInBlocks);
    }

    @Override
    public void onCoinsReceived(BigInteger newBalance)
    {
        log.info("onCoinsReceived " + newBalance);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void buildScreen()
    {
        gridRow = 1;
        minAmountTextField = FormBuilder.addTextField(formGridPane, "Min. Amount:", String.valueOf(settings.getMaxCollateral()), ++gridRow, true, true);
        collateralTextField = FormBuilder.addTextField(formGridPane, "Collateral (%):", String.valueOf(settings.getMaxCollateral() * 100), ++gridRow, true, true);

        FormBuilder.addVSpacer(formGridPane, ++gridRow);
        FormBuilder.addHeaderLabel(formGridPane, "Offer details:", ++gridRow);
        FormBuilder.addTextField(formGridPane, "Bank account type:", Localisation.get(user.getCurrentBankAccount().getBankAccountType().getType().toString()), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Bank account currency:", user.getCurrentBankAccount().getCurrency().getCurrencyCode(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Bank account county:", user.getCurrentBankAccount().getCountryLocale().getDisplayCountry(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Accepted countries:", Formatter.countryLocalesToString(settings.getAcceptedCountryLocales()), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Accepted languages:", Formatter.languageLocalesToString(settings.getAcceptedLanguageLocales()), ++gridRow);

        FormBuilder.addVSpacer(formGridPane, ++gridRow);
        Label placeOfferTitle = FormBuilder.addHeaderLabel(formGridPane, "Place offer:", ++gridRow);

        TextField feeLabel = FormBuilder.addTextField(formGridPane, "Offer fee:", BtcFormatter.formatSatoshis(Fees.OFFER_CREATION_FEE, true), ++gridRow);
        feeLabel.setMouseTransparent(true);

        placeOfferButton = new Button("Place offer");
        formGridPane.add(placeOfferButton, 1, ++gridRow);
        placeOfferButton.setDefaultButton(true);

        // handlers
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

        placeOfferButton.setOnAction(e -> {
            if (!inputValid())
            {
                Popups.openWarningPopup("Invalid input", "Your input is invalid");
                return;
            }

            double collateralAsDouble = Converter.stringToDouble(collateralTextField.getText()) / 100;
            Arbitrator arbitrator = settings.getRandomArbitrator(collateralAsDouble, getAmountAsBI());
            if (arbitrator == null)
            {
                Popups.openWarningPopup("No arbitrator available", "No arbitrator from your arbitrator list does match the collateral and amount value.");
                return;
            }

            offer = new Offer(user.getAccountID(),
                    user.getMessageID(),
                    direction,
                    Converter.stringToDouble(priceTextField.getText()),
                    BtcFormatter.stringValueToSatoshis(amountTextField.getText()),
                    BtcFormatter.stringValueToSatoshis(minAmountTextField.getText()),
                    user.getCurrentBankAccount().getBankAccountType().getType(),
                    user.getCurrentBankAccount().getCurrency(),
                    user.getCurrentBankAccount().getCountryLocale(),
                    arbitrator,
                    collateralAsDouble,
                    settings.getAcceptedCountryLocales(),
                    settings.getAcceptedLanguageLocales());

            FutureCallback callback = new FutureCallback<Transaction>()
            {
                @Override
                public void onSuccess(Transaction transaction)
                {
                    log.info("sendResult onSuccess:" + transaction.toString());
                    offer.setOfferPaymentTxID(transaction.getHashAsString());
                    buildConfirmationView(transaction.getHashAsString());
                    placeOfferTitle.setText("Transaction sent:");
                    formGridPane.getChildren().remove(placeOfferButton);
                }

                @Override
                public void onFailure(Throwable t)
                {
                    log.warn("sendResult onFailure:" + t.toString());
                    Popups.openErrorPopup("Fee payment failed", "Fee payment failed. " + t.toString());
                }
            };
            try
            {
                trading.placeNewOffer(offer, callback);

            } catch (InsufficientMoneyException e1)
            {
                Popups.openErrorPopup("Not enough money available", "There is not enough money available. Please pay in first to your wallet.");
            }

        });
    }

    private void buildConfirmationView(String txID)
    {
        FormBuilder.addTextField(formGridPane, "Transaction ID:", txID, ++gridRow, false, true);

        new ConfirmationComponent(walletFacade, formGridPane, ++gridRow);

        Button closeButton = new Button("Close");
        formGridPane.add(closeButton, 1, ++gridRow);
        closeButton.setDefaultButton(true);


        closeButton.setOnAction(e -> {
            TabPane tabPane = ((TabPane) (holderPane.getParent().getParent()));
            tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

            navigationController.navigateToView(NavigationController.ORDER_BOOK, "Orderbook");
        });
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
                minAmountAsDouble <= amountAsDouble &&
                collateralAsDouble >= settings.getMinCollateral() &&
                collateralAsDouble <= settings.getMaxCollateral();
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
}

