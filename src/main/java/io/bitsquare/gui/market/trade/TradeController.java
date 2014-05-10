package io.bitsquare.gui.market.trade;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.Fees;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.components.processbar.ProcessStepBar;
import io.bitsquare.gui.components.processbar.ProcessStepItem;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.FormBuilder;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.gui.util.Popups;
import io.bitsquare.trade.*;
import io.bitsquare.util.Utils;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class TradeController implements Initializable, ChildController, WalletFacade.WalletListener
{
    private static final Logger log = LoggerFactory.getLogger(TradeController.class);
    private static final int SIM_DELAY = 2000;

    private Trading trading;
    private WalletFacade walletFacade;
    private BlockChainFacade blockChainFacade;
    private Offer offer;
    private Trade trade;
    private Contract contract;
    private BigInteger requestedAmount;
    private boolean offererIsOnline;
    private int row;

    private List<ProcessStepItem> processStepItems = new ArrayList();

    private NavigationController navigationController;
    private TextField amountTextField, totalToPayLabel, totalLabel, collateralTextField;
    private Label statusTextField, infoLabel;
    private Button nextButton;
    private ProgressBar progressBar;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private ProcessStepBar<String> processStepBar;
    @FXML
    private GridPane gridPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeController(Trading trading, WalletFacade walletFacade, BlockChainFacade blockChainFacade)
    {
        this.trading = trading;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer, BigInteger requestedAmount)
    {
        this.offer = offer;
        this.requestedAmount = requestedAmount.compareTo(BigInteger.ZERO) > 0 ? requestedAmount : offer.getAmount();

        trade = trading.createTrade(offer);
        trade.setTradeAmount(requestedAmount);
        contract = trading.createContract(trade);

        processStepItems.add(new ProcessStepItem(takerIsSelling() ? "Sell BTC" : "Buy BTC"));
        processStepItems.add(new ProcessStepItem("Bank transfer"));
        processStepItems.add(new ProcessStepItem("Completed"));
        processStepBar.setProcessStepItems(processStepItems);

        buildTakeOfferScreen();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        walletFacade.addRegistrationWalletListener(this);
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

    // trade process
    private void buildTakeOfferScreen()
    {
        row = -1;

        FormBuilder.addHeaderLabel(gridPane, "Take offer:", ++row);
        amountTextField = FormBuilder.addTextField(gridPane, "Amount (BTC):", BtcFormatter.formatSatoshis(requestedAmount, false), ++row, true, true);
        amountTextField.textProperty().addListener(e -> {
            applyVolume();
            applyCollateral();
            totalToPayLabel.setText(getTotalToPay());

        });
        Label amountRangeLabel = new Label("(" + BtcFormatter.formatSatoshis(offer.getMinAmount(), false) + " - " + BtcFormatter.formatSatoshis(offer.getAmount(), false) + ")");
        gridPane.add(amountRangeLabel, 2, row);

        FormBuilder.addTextField(gridPane, "Price (" + offer.getCurrency() + "/BTC):", Formatter.formatPrice(offer.getPrice()), ++row);
        totalLabel = FormBuilder.addTextField(gridPane, "Total (" + offer.getCurrency() + "):", Formatter.formatVolume(getVolume()), ++row);
        collateralTextField = FormBuilder.addTextField(gridPane, "Collateral (BTC):", "", ++row);
        applyCollateral();
        FormBuilder.addTextField(gridPane, "Offer fee (BTC):", BtcFormatter.formatSatoshis(Fees.OFFER_TAKER_FEE, false), ++row);
        totalToPayLabel = FormBuilder.addTextField(gridPane, "Total to pay (BTC):", getTotalToPay(), ++row);

        nextButton = FormBuilder.addButton(gridPane, "Take offer and pay", ++row);
        nextButton.setDefaultButton(true);
        nextButton.setOnAction(e -> initTrade());

        // details
        FormBuilder.addVSpacer(gridPane, ++row);
        FormBuilder.addHeaderLabel(gridPane, "Offerer details:", ++row);
        TextField isOnlineTextField = FormBuilder.addTextField(gridPane, "Online status:", "Checking offerers online status...", ++row);
        ProgressIndicator isOnlineChecker = new ProgressIndicator();
        isOnlineChecker.setPrefSize(20, 20);
        isOnlineChecker.setLayoutY(3);
        Pane isOnlineCheckerHolder = new Pane();
        isOnlineCheckerHolder.getChildren().addAll(isOnlineChecker);
        gridPane.add(isOnlineCheckerHolder, 2, row);
        checkIfOffererIsOnline(isOnlineCheckerHolder, isOnlineTextField);

        FormBuilder.addTextField(gridPane, "Bank account type:", offer.getBankAccountTypeEnum().toString(), ++row);
        FormBuilder.addTextField(gridPane, "Bank account country:", offer.getBankAccountCountryLocale().getDisplayCountry(), ++row);
        FormBuilder.addTextField(gridPane, "Arbitrator:", offer.getArbitrator().getName(), ++row);
        Label arbitratorLink = new Label(offer.getArbitrator().getUrl());
        arbitratorLink.setId("label-url");
        gridPane.add(arbitratorLink, 2, row);
        arbitratorLink.setOnMouseClicked(e -> {
            try
            {
                Utils.openURL(offer.getArbitrator().getUrl());
            } catch (Exception e1)
            {
                log.warn(e1.toString());
            }
        });

        FormBuilder.addVSpacer(gridPane, ++row);
        FormBuilder.addHeaderLabel(gridPane, "More details:", ++row);
        FormBuilder.addTextField(gridPane, "Offer ID:", offer.getUid().toString(), ++row);
        FormBuilder.addTextField(gridPane, "Account ID:", offer.getAccountID(), ++row);
        FormBuilder.addTextField(gridPane, "Messaging ID:", offer.getMessageID(), ++row);
        FormBuilder.addTextField(gridPane, "Supported languages:", Formatter.languageLocalesToString(offer.getAcceptedLanguageLocales()), ++row);
        FormBuilder.addTextField(gridPane, "Supported countries:", Formatter.countryLocalesToString(offer.getAcceptedCountryLocales()), ++row);
    }

    private void initTrade()
    {
        if (!tradeAmountValid())
        {
            Popups.openErrorPopup("Your input is not valid", "The requested amount you entered is outside of the range of the offered amount.");
            return;
        }

        if (!blockChainFacade.verifyEmbeddedData(offer.getAccountID()))
        {
            Popups.openErrorPopup("Offerers account ID not valid", "Offerers registration tx is not found in blockchain or does not match the requirements.");
            return;
        }

        if (blockChainFacade.isAccountIDBlacklisted(offer.getAccountID()))
        {
            Popups.openErrorPopup("Offerers account ID is blacklisted", "Offerers account ID is blacklisted.");
            return;
        }

        amountTextField.setEditable(false);

        gridPane.getChildren().clear();

        row = -1;
        FormBuilder.addHeaderLabel(gridPane, "Trade request inited", ++row, 0);

        statusTextField = FormBuilder.addLabel(gridPane, "Current activity:", "Request confirmation from offerer to take that offer.", ++row);
        GridPane.setColumnSpan(statusTextField, 2);
        FormBuilder.addLabel(gridPane, "Progress:", "", ++row);
        progressBar = new ProgressBar();
        progressBar.setProgress(0.0);
        progressBar.setPrefWidth(300);
        GridPane.setFillWidth(progressBar, true);
        gridPane.add(progressBar, 1, row);

        FormBuilder.addLabel(gridPane, "Status:", "", ++row);
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setLayoutY(2);
        Pane progressIndicatorHolder = new Pane();
        progressIndicatorHolder.getChildren().addAll(progressIndicator);
        gridPane.add(progressIndicatorHolder, 1, row);

        trade.setTradeAmount(BtcFormatter.stringValueToSatoshis(amountTextField.getText()));

        trading.sendTakeOfferRequest(trade);
        Utils.setTimeout(SIM_DELAY, (AnimationTimer animationTimer) -> {
            onTakeOfferRequestConfirmed();
            progressBar.setProgress(1.0 / 3.0);
            return null;
        });
    }

    private void onTakeOfferRequestConfirmed()
    {
        FutureCallback callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(Transaction transaction)
            {
                log.info("sendResult onSuccess:" + transaction.toString());
                trade.setTakeOfferFeeTxID(transaction.getHashAsString());

                statusTextField.setText("Offer fee payed. Send offerer payment transaction ID for confirmation.");
                Utils.setTimeout(SIM_DELAY, (AnimationTimer animationTimer) -> {
                    onOfferFeePaymentConfirmed();
                    progressBar.setProgress(2.0 / 3.0);
                    return null;
                });
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
            trading.payOfferFee(trade, callback);
        } catch (InsufficientMoneyException e)
        {
            Popups.openErrorPopup("Not enough money available", "There is not enough money available. Please pay in first to your wallet.");
        }
    }

    private void onOfferFeePaymentConfirmed()
    {
        trading.requestOffererDetailData();
        statusTextField.setText("Request bank account details from offerer.");
        Utils.setTimeout(SIM_DELAY, (AnimationTimer animationTimer) -> {
            onUserDetailsReceived();
            progressBar.setProgress(1.0);
            return null;
        });
    }

    private void onUserDetailsReceived()
    {
        if (!blockChainFacade.verifyEmbeddedData(offer.getAccountID()))
        {
            Popups.openErrorPopup("Offerers bank account is blacklisted", "Offerers bank account is blacklisted.");
            return;
        }

        trading.signContract(contract);
        trading.payToDepositTx(trade);

        buildWaitBankTransfer();
    }

    private void buildWaitBankTransfer()
    {
        processStepBar.next();

        gridPane.getChildren().clear();

        row = -1;
        FormBuilder.addHeaderLabel(gridPane, "Bank transfer", ++row, 0);
        infoLabel = FormBuilder.addLabel(gridPane, "Status:", "Wait for Bank transfer.", ++row);

        Utils.setTimeout(SIM_DELAY, (AnimationTimer animationTimer) -> {
            onBankTransferInited();
            return null;
        });
    }

    private void onBankTransferInited()
    {
        row = 1;
        infoLabel.setText("Bank transfer has been inited.");
        Label label = FormBuilder.addLabel(gridPane, "", "Check your bank account and continue when you have received the money.", ++row);
        GridPane.setColumnSpan(label, 2);

        gridPane.add(nextButton, 1, ++row);
        nextButton.setText("I have received the bank transfer");
        nextButton.setOnAction(e -> releaseBTC());
    }

    private void releaseBTC()
    {
        processStepBar.next();
        trading.releaseBTC(trade);

        nextButton.setText("Close");
        nextButton.setOnAction(e -> close());

        gridPane.getChildren().clear();
        row = -1;
        FormBuilder.addHeaderLabel(gridPane, "Trade successfully completed", ++row);
        FormBuilder.addTextField(gridPane, "You have payed in total (BTC):", getTotalToPay(), ++row);
        if (takerIsSelling())
        {
            FormBuilder.addTextField(gridPane, "You got returned collateral (BTC):", BtcFormatter.formatSatoshis(getCollateralInSatoshis(), false), ++row);
            FormBuilder.addTextField(gridPane, "You have received (" + offer.getCurrency() + "):", Formatter.formatVolume(getVolume()), ++row);
        }
        else
        {
            FormBuilder.addTextField(gridPane, "You got returned collateral (BTC):", BtcFormatter.formatSatoshis(getCollateralInSatoshis(), false), ++row);
            FormBuilder.addTextField(gridPane, "You have received (" + offer.getCurrency() + "):", Formatter.formatVolume(getVolume()), ++row);
            FormBuilder.addTextField(gridPane, "You have received (BTC):", BtcFormatter.formatSatoshis(offer.getAmount(), false), ++row);
        }

        gridPane.add(nextButton, 1, ++row);
    }

    private void close()
    {
        walletFacade.removeRegistrationWalletListener(this);

        TabPane tabPane = ((TabPane) (rootContainer.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

        navigationController.navigateToView(NavigationController.ORDER_BOOK, "Orderbook");
    }

    // Other Private methods
    private boolean tradeAmountValid()
    {
        BigInteger tradeAmount = BtcFormatter.stringValueToSatoshis(amountTextField.getText());
        return tradeAmount.compareTo(offer.getAmount()) <= 0 && tradeAmount.compareTo(offer.getMinAmount()) >= 0;
    }

    private boolean takerIsSelling()
    {
        return offer.getDirection() == Direction.BUY;
    }


    private void checkIfOffererIsOnline(Node isOnlineChecker, TextField isOnlineTextField)
    {
        // mock
        Utils.setTimeout(3000, (AnimationTimer animationTimer) -> {
            offererIsOnline = Math.random() > 0.3 ? true : false;
            isOnlineTextField.setText(offererIsOnline ? "Online" : "Offline");
            gridPane.getChildren().remove(isOnlineChecker);
            return null;
        });
    }

    private void applyVolume()
    {
        totalLabel.setText(Formatter.formatVolume(getVolume(), offer.getCurrency()));
    }

    private double getVolume()
    {
        return offer.getPrice() * Converter.stringToDouble(amountTextField.getText());
    }

    private String getTotalToPay()
    {
        if (takerIsSelling())
        {
            return BtcFormatter.formatSatoshis(getAmountInSatoshis().add(Fees.OFFER_TAKER_FEE).add(getCollateralInSatoshis()), false);
        }
        else
        {
            return BtcFormatter.formatSatoshis(Fees.OFFER_TAKER_FEE.add(getCollateralInSatoshis()), false) + "\n" +
                    Formatter.formatVolume(getVolume(), offer.getCurrency());
        }
    }


    private void applyCollateral()
    {
        collateralTextField.setText(BtcFormatter.formatSatoshis(getCollateralInSatoshis(), false));
    }

    private BigInteger getCollateralInSatoshis()
    {
        return BtcFormatter.doubleValueToSatoshis(Converter.stringToDouble(amountTextField.getText()) * offer.getCollateral());
    }

    private BigInteger getAmountInSatoshis()
    {
        return BtcFormatter.stringValueToSatoshis(amountTextField.getText());
    }
}

