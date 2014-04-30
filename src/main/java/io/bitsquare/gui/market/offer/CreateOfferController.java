package io.bitsquare.gui.market.offer;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.inject.Inject;
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
import io.bitsquare.user.User;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import org.controlsfx.dialog.Dialogs;
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

    private Button placeOfferButton;
    private int gridRow;

    @FXML
    private AnchorPane holderPane;
    @FXML
    private GridPane formGridPane;
    @FXML
    public Label buyLabel;
    @FXML
    public TextField volume, amount, price, minAmount;

    @Inject
    public CreateOfferController(Trading trading, WalletFacade walletFacade, Settings settings, User user)
    {
        this.trading = trading;
        this.walletFacade = walletFacade;
        this.settings = settings;
        this.user = user;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        walletFacade.addRegistrationWalletListener(this);

        gridRow = 2;
        FormBuilder.addVSpacer(formGridPane, ++gridRow);
        FormBuilder.addHeaderLabel(formGridPane, "Offer details:", ++gridRow);
        FormBuilder.addTextField(formGridPane, "Bank account type:", Localisation.get(user.getCurrentBankAccount().getBankAccountType().getType().toString()), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Bank account currency:", user.getCurrentBankAccount().getCurrency().getCurrencyCode(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Bank account county:", user.getCurrentBankAccount().getCountryLocale().getDisplayCountry(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Accepted countries:", Formatter.countryLocalesToString(settings.getAcceptedCountryLocales()), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Accepted languages:", Formatter.languageLocalesToString(settings.getAcceptedLanguageLocales()), ++gridRow);

        FormBuilder.addVSpacer(formGridPane, ++gridRow);
        Label placeOfferTitle = FormBuilder.addHeaderLabel(formGridPane, "Place offer:", ++gridRow);

        TextField feeLabel = FormBuilder.addTextField(formGridPane, "Offer fee:", Formatter.formatSatoshis(Fees.OFFER_CREATION_FEE, true), ++gridRow);
        feeLabel.setMouseTransparent(true);

        placeOfferButton = new Button("Place offer");
        formGridPane.add(placeOfferButton, 1, ++gridRow);
        placeOfferButton.setDefaultButton(true);

        // handlers
        amount.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                updateVolume();
            }
        });

        price.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                updateVolume();
            }
        });

        placeOfferButton.setOnAction(e -> {

            if (inputValid())
            {
                Offer offer = new Offer(user.getAccountID(),
                        user.getMessageID(),
                        direction,
                        Converter.stringToDouble(price.getText()),
                        Converter.stringToDouble(amount.getText()),
                        Converter.stringToDouble(minAmount.getText()),
                        user.getCurrentBankAccount().getBankAccountType().getType(),
                        user.getCurrentBankAccount().getCurrency(),
                        user.getCurrentBankAccount().getCountryLocale(),
                        settings.getRandomArbitrator(),
                        settings.getAcceptedCountryLocales(),
                        settings.getAcceptedLanguageLocales());

                try
                {
                    String txID = trading.placeNewOffer(offer);
                    formGridPane.getChildren().remove(placeOfferButton);
                    placeOfferTitle.setText("Transaction sent:");
                    buildConfirmationView(txID);
                } catch (InsufficientMoneyException e1)
                {
                    Dialogs.create()
                            .title("Not enough money available")
                            .message("There is not enough money available. Please pay in first to your wallet.")
                            .nativeTitleBar()
                            .lightweight()
                            .showError();
                }
            }
        });
    }


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

    private void buildConfirmationView(String txID)
    {
        FormBuilder.addTextField(formGridPane, "Transaction ID:", txID, ++gridRow, false, true);

        ConfirmationComponent confirmationComponent = new ConfirmationComponent(walletFacade, formGridPane, ++gridRow);

        Button closeButton = new Button("Close");
        formGridPane.add(closeButton, 1, ++gridRow);
        closeButton.setDefaultButton(true);


        closeButton.setOnAction(e -> {
            TabPane tabPane = ((TabPane) (holderPane.getParent().getParent()));
            tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

            navigationController.navigateToView(NavigationController.ORDER_BOOK, "Orderbook");
        });
    }


    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    public void setOrderBookFilter(OrderBookFilter orderBookFilter)
    {
        direction = orderBookFilter.getDirection();
        amount.setText(Formatter.formatPrice(orderBookFilter.getAmount()));
        minAmount.setText(Formatter.formatPrice(orderBookFilter.getAmount()));
        price.setText(Formatter.formatPrice(orderBookFilter.getPrice()));

        String iconPath = (direction == Direction.BUY) ? Icons.BUY : Icons.SELL;
        buyLabel.setText(Formatter.formatDirection(direction, false) + ":");
        updateVolume();
    }

    //TODO
    private boolean inputValid()
    {
        return true;
    }

    private void updateVolume()
    {
        double amountAsDouble = Converter.stringToDouble(amount.getText());
        double priceAsDouble = Converter.stringToDouble(price.getText());
        volume.setText(Formatter.formatPrice(amountAsDouble * priceAsDouble));
    }

    private Image getConfirmIconImage(int numBroadcastPeers, int depthInBlocks)
    {
        if (depthInBlocks > 0)
            return Icons.getIconImage(Icons.getIconIDForConfirmations(depthInBlocks));
        else
            return Icons.getIconImage(Icons.getIconIDForPeersSeenTx(numBroadcastPeers));
    }

    private String getConfirmationsText(int registrationConfirmationNumBroadcastPeers, int registrationConfirmationDepthInBlocks)
    {
        return registrationConfirmationDepthInBlocks + " confirmation(s) / " + "Seen by " + registrationConfirmationNumBroadcastPeers + " peer(s)";
    }

}

