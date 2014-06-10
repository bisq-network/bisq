package io.bitsquare.gui.market.trade;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
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
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.TradeMessage;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.Trading;
import io.bitsquare.trade.taker.TakerPaymentProtocol;
import io.bitsquare.trade.taker.TakerPaymentProtocolListener;
import io.bitsquare.util.Utilities;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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

public class TakerTradeController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(TakerTradeController.class);

    private Trading trading;
    private WalletFacade walletFacade;
    private BlockChainFacade blockChainFacade;
    private MessageFacade messageFacade;
    private Offer offer;
    private Trade trade;
    private BigInteger requestedAmount;
    private boolean offererIsOnline;
    private int row;

    private List<ProcessStepItem> processStepItems = new ArrayList();

    private NavigationController navigationController;
    private TextField amountTextField, totalToPayLabel, totalLabel, collateralTextField, isOnlineTextField;
    private Label statusTextField, infoLabel;
    private Button nextButton;
    private ProgressBar progressBar;
    private AnimationTimer checkOnlineStatusTimer;
    private Pane isOnlineCheckerHolder;
    TakerPaymentProtocol takerPaymentProtocol;
    private Label headerLabel;

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
    public TakerTradeController(Trading trading, WalletFacade walletFacade, BlockChainFacade blockChainFacade, MessageFacade messageFacade)
    {
        this.trading = trading;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
        this.messageFacade = messageFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer, BigInteger requestedAmount)
    {
        this.offer = offer;
        this.requestedAmount = requestedAmount.compareTo(BigInteger.ZERO) > 0 ? requestedAmount : offer.getAmount();

        // trade = trading.createTrade(offer);
        //trade.setTradeAmount(requestedAmount);
        //contract = trading.createContract(trade);

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
        if (checkOnlineStatusTimer != null)
        {
            checkOnlineStatusTimer.stop();
            checkOnlineStatusTimer = null;
        }
    }


    //TODO
    public void onPingPeerResult(boolean success)
    {
        setIsOnlineStatus(success);
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
        FormBuilder.addTextField(gridPane, "Offer fee (BTC):", Utils.bitcoinValueToFriendlyString(Fees.OFFER_TAKER_FEE.add(Fees.TX_FEE)), ++row);
        totalToPayLabel = FormBuilder.addTextField(gridPane, "Total to pay (BTC):", getTotalToPay(), ++row);

        isOnlineTextField = FormBuilder.addTextField(gridPane, "Online status:", "Checking offerers online status...", ++row);
        ProgressIndicator isOnlineChecker = new ProgressIndicator();
        isOnlineChecker.setPrefSize(20, 20);
        isOnlineChecker.setLayoutY(3);
        isOnlineCheckerHolder = new Pane();
        isOnlineCheckerHolder.getChildren().addAll(isOnlineChecker);
        gridPane.add(isOnlineCheckerHolder, 2, row);

        //TODO
        messageFacade.pingPeer(offer.getMessagePubKeyAsHex());
        checkOnlineStatusTimer = Utilities.setTimeout(1000, (AnimationTimer animationTimer) -> {
            setIsOnlineStatus(true);
            return null;
        });

        nextButton = FormBuilder.addButton(gridPane, "Take offer and pay", ++row);
        nextButton.setDefaultButton(true);
        nextButton.setOnAction(e -> takeOffer());

        // details
        FormBuilder.addVSpacer(gridPane, ++row);
        FormBuilder.addHeaderLabel(gridPane, "Offerer details:", ++row);
        FormBuilder.addTextField(gridPane, "Bank account type:", offer.getBankAccountTypeEnum().toString(), ++row);
        FormBuilder.addTextField(gridPane, "Country:", offer.getBankAccountCountryLocale().getDisplayCountry(), ++row);
        FormBuilder.addTextField(gridPane, "Arbitrator:", offer.getArbitrator().getName(), ++row);
        Label arbitratorLink = new Label(offer.getArbitrator().getUrl());
        arbitratorLink.setId("label-url");
        gridPane.add(arbitratorLink, 2, row);
        arbitratorLink.setOnMouseClicked(e -> {
            try
            {
                Utilities.openURL(offer.getArbitrator().getUrl());
            } catch (Exception e1)
            {
                log.warn(e1.toString());
            }
        });

        FormBuilder.addTextField(gridPane, "Supported languages:", Formatter.languageLocalesToString(offer.getAcceptedLanguageLocales()), ++row);
        FormBuilder.addTextField(gridPane, "Supported countries:", Formatter.countryLocalesToString(offer.getAcceptedCountryLocales()), ++row);
    }

    private void takeOffer()
    {
        if (!tradeAmountValid())
        {
            Popups.openErrorPopup("Your input is not valid", "The requested amount you entered is outside of the range of the offered amount.");
            return;
        }

       /* if (!blockChainFacade.verifyAccountRegistration(offer.getAccountID()))
        {
            Popups.openErrorPopup("Offerers account ID not valid", "Offerers registration tx is not found in blockchain or does not match the requirements.");
            return;
        }

        if (blockChainFacade.isAccountIDBlacklisted(offer.getAccountID()))
        {
            Popups.openErrorPopup("Offerers account ID is blacklisted", "Offerers account ID is blacklisted.");
            return;
        }  */

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

        trade = trading.createTrade(offer);
        trade.setTradeAmount(BtcFormatter.stringValueToSatoshis(amountTextField.getText()));

        takerPaymentProtocol = trading.addTakerPaymentProtocol(trade, new TakerPaymentProtocolListener()
        {
            @Override
            public void onProgress(double progress)
            {
                progressBar.setProgress(progress);

                /*switch (state)
                {
                    case FOUND_PEER_ADDRESS:
                        statusTextField.setText("Peer found.");
                        break;
                    case SEND_TAKE_OFFER_REQUEST_ARRIVED:
                        statusTextField.setText("Take offer request successfully sent to peer.");
                        break;
                    case SEND_TAKE_OFFER_REQUEST_ACCEPTED:
                        statusTextField.setText("Take offer request accepted by peer.");
                        break;
                    case SEND_TAKE_OFFER_REQUEST_REJECTED:
                        statusTextField.setText("Take offer request rejected by peer.");
                        break;
                    case INSUFFICIENT_MONEY_FOR_OFFER_FEE:
                        Popups.openErrorPopup("Not enough money available", "There is not enough money available. Please pay in first to your wallet.");
                        break;
                    case OFFER_FEE_PAYED:
                        statusTextField.setText("Offer fee payed. Send offerer payment transaction ID for confirmation.");
                        break;
                }  */
            }

            @Override
            public void onFailure(String failureMessage)
            {
                log.warn(failureMessage);
            }

            @Override
            public void onDepositTxPublished(String depositTxID)
            {
                buildDepositPublishedScreen(depositTxID);
            }

            @Override
            public void onBankTransferInited(TradeMessage tradeMessage)
            {
                buildBankTransferInitedScreen(tradeMessage);
            }

            @Override
            public void onTradeCompleted(String hashAsString)
            {
                showSummary(hashAsString);
            }
        });

        takerPaymentProtocol.takeOffer();
    }

    private void updateTx(Trade trade)
    {

    }

    private void buildDepositPublishedScreen(String depositTxID)
    {
        gridPane.getChildren().clear();

        row = -1;
        headerLabel = FormBuilder.addHeaderLabel(gridPane, "Deposit transaction published", ++row, 0);
        infoLabel = FormBuilder.addLabel(gridPane, "Status:", "Deposit transaction published by offerer.\nAs soon as the offerer starts the \nBank transfer, you will get informed.", ++row);
        FormBuilder.addTextField(gridPane, "Transaction ID:", depositTxID, ++row, false, true);

        // todo need to load that tx from blockchain, or listen to blockchain
        //   confidenceDisplay = new ConfidenceDisplay(walletFacade.getWallet(), confirmationLabel, transaction, progressIndicator);
    }

    private void buildBankTransferInitedScreen(TradeMessage tradeMessage)
    {
        processStepBar.next();

        headerLabel.setText("Bank transfer inited");
        infoLabel.setText("Check your bank account and continue \nwhen you have received the money.");
        gridPane.add(nextButton, 1, ++row);
        nextButton.setText("I have received the money at my bank");
        nextButton.setOnAction(e -> releaseBTC(tradeMessage));
    }

    private void releaseBTC(TradeMessage tradeMessage)
    {
        processStepBar.next();
        trading.releaseBTC(trade.getUid(), tradeMessage);

        nextButton.setText("Close");
        nextButton.setOnAction(e -> close());

        gridPane.getChildren().clear();
        row = -1;
        FormBuilder.addHeaderLabel(gridPane, "Trade successfully completed", ++row);

        String fiatReceived = Formatter.formatVolume(trade.getOffer().getPrice() * BtcFormatter.satoshiToBTC(trade.getTradeAmount()));

        FormBuilder.addTextField(gridPane, "You have sold (BTC):", Utils.bitcoinValueToFriendlyString(trade.getTradeAmount()), ++row);
        if (takerIsSelling())
        {
            FormBuilder.addTextField(gridPane, "You have received (" + offer.getCurrency() + "):\"", fiatReceived, ++row);
            FormBuilder.addTextField(gridPane, "Total fees (take offer fee + tx fee):", Utils.bitcoinValueToFriendlyString(Fees.OFFER_TAKER_FEE.add(Fees.TX_FEE)), ++row);
            FormBuilder.addTextField(gridPane, "Refunded collateral:", Utils.bitcoinValueToFriendlyString(trade.getCollateralAmount()), ++row);
        }
        else
        {
            //TODO
            FormBuilder.addTextField(gridPane, "You got returned collateral (BTC):", BtcFormatter.formatSatoshis(getCollateralInSatoshis(), false), ++row);
            FormBuilder.addTextField(gridPane, "You have received (" + offer.getCurrency() + "):", Formatter.formatVolume(getVolume()), ++row);
            FormBuilder.addTextField(gridPane, "You have received (BTC):", BtcFormatter.formatSatoshis(offer.getAmount(), false), ++row);
        }

        gridPane.add(nextButton, 1, ++row);
    }

    private void showSummary(String hashAsString)
    {
        gridPane.getChildren().clear();

    }

    private void close()
    {
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

    private void setIsOnlineStatus(boolean isOnline)
    {
        if (checkOnlineStatusTimer != null)
        {
            checkOnlineStatusTimer.stop();
            checkOnlineStatusTimer = null;
        }

        offererIsOnline = isOnline;
        isOnlineTextField.setText(offererIsOnline ? "Online" : "Offline");
        gridPane.getChildren().remove(isOnlineCheckerHolder);

        isOnlineTextField.setId(isOnline ? "online-label" : "offline-label");
        isOnlineTextField.layout();
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
            return BtcFormatter.formatSatoshis(getAmountInSatoshis().add(Fees.OFFER_TAKER_FEE).add(Transaction.MIN_NONDUST_OUTPUT).add(Fees.TX_FEE).add(getCollateralInSatoshis()), false);
        }
        else
        {
            return BtcFormatter.formatSatoshis(Fees.OFFER_TAKER_FEE.add(Transaction.MIN_NONDUST_OUTPUT).add(Fees.TX_FEE).add(getCollateralInSatoshis()), false) + "\n" +
                    Formatter.formatVolume(getVolume(), offer.getCurrency());
        }
    }


    private void applyCollateral()
    {
        collateralTextField.setText(BtcFormatter.formatSatoshis(getCollateralInSatoshis(), false));
    }

    private BigInteger getCollateralInSatoshis()
    {
        double amount = Converter.stringToDouble(amountTextField.getText());
        double resultDouble = amount * (double) offer.getCollateral() / 100.0;
        BigInteger result = BtcFormatter.doubleValueToSatoshis(resultDouble);
        return result;
    }

    private BigInteger getAmountInSatoshis()
    {
        return BtcFormatter.stringValueToSatoshis(amountTextField.getText());
    }
}

