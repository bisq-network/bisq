package io.bitsquare.gui.trade.tradeprocess;

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
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.*;
import io.bitsquare.user.User;
import io.bitsquare.util.Utils;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.controlsfx.dialog.Dialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class TradeProcessController implements Initializable, ChildController, WalletFacade.WalletListener
{
    private static final Logger log = LoggerFactory.getLogger(TradeProcessController.class);
    private static final int SIM_DELAY = 1000;

    private Trading trading;
    private User user;
    private WalletFacade walletFacade;
    private BlockChainFacade blockChainFacade;
    private Settings settings;
    private Storage storage;
    private Offer offer;
    private Trade trade;
    private Contract contract;
    private double requestedAmount;
    private boolean offererIsOnline;

    private List<ProcessStepItem> processStepItems = new ArrayList();

    private NavigationController navigationController;
    private TextField amountTextField, totalToPayLabel, totalLabel;
    private Label statusTextField;
    private Button nextButton;
    private ProgressBar progressBar;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private ProcessStepBar<String> processStepBar;
    @FXML
    private GridPane formGridPane;
    @FXML
    private VBox vBox;
    private Label infoLabel;
    private int gridRow;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor(s)
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeProcessController(Trading trading, User user, WalletFacade walletFacade, BlockChainFacade blockChainFacade, Settings settings, Storage storage)
    {
        this.trading = trading;
        this.user = user;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
        this.settings = settings;
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer, double requestedAmount)
    {
        this.offer = offer;
        this.requestedAmount = requestedAmount > 0 ? requestedAmount : offer.getAmount();

        trade = trading.createNewTrade(offer);
        trade.setTradeAmount(requestedAmount);
        contract = trading.createNewContract(trade);

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // trade process
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void buildTakeOfferScreen()
    {

        int gridRow = -1;

        FormBuilder.addHeaderLabel(formGridPane, "Take offer:", ++gridRow);
        amountTextField = FormBuilder.addTextField(formGridPane, "Amount BTC:", Formatter.formatAmount(requestedAmount), ++gridRow, true, true);
        amountTextField.textProperty().addListener(e -> {
            setVolume();
            totalToPayLabel.setText(getTotalToPay());

        });
        Label amountRangeLabel = new Label("(" + Formatter.formatAmount(offer.getMinAmount()) + " - " + Formatter.formatAmount(offer.getAmount()) + ")");
        formGridPane.add(amountRangeLabel, 2, gridRow);

        FormBuilder.addTextField(formGridPane, "Price:", Formatter.formatPriceWithCurrencyPair(offer.getPrice(), offer.getCurrency()), ++gridRow);
        totalLabel = FormBuilder.addTextField(formGridPane, "Total:", Formatter.formatVolume(getVolume(), offer.getCurrency()), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Offer fee:", Formatter.formatSatoshis(Fees.OFFER_TAKER_FEE, true), ++gridRow);
        totalToPayLabel = FormBuilder.addTextField(formGridPane, "Total to pay:", getTotalToPay(), ++gridRow);

        nextButton = FormBuilder.addButton(formGridPane, "Take offer and pay", ++gridRow);
        nextButton.setDefaultButton(true);
        nextButton.setOnAction(e -> {
            initTrade();
        });

        // details
        FormBuilder.addVSpacer(formGridPane, ++gridRow);
        FormBuilder.addHeaderLabel(formGridPane, "Offerer details:", ++gridRow);
        TextField isOnlineTextField = FormBuilder.addTextField(formGridPane, "Online status:", "Checking offerers online status...", ++gridRow);
        ProgressIndicator isOnlineChecker = new ProgressIndicator();
        isOnlineChecker.setPrefSize(20, 20);
        isOnlineChecker.setLayoutY(3);
        Pane isOnlineCheckerHolder = new Pane();
        isOnlineCheckerHolder.getChildren().addAll(isOnlineChecker);
        formGridPane.add(isOnlineCheckerHolder, 2, gridRow);
        checkIfOffererIsOnline(isOnlineCheckerHolder, isOnlineTextField);

        FormBuilder.addTextField(formGridPane, "Bank account type:", offer.getBankAccountTypeEnum().toString(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Bank account country:", offer.getBankAccountCountryLocale().getDisplayCountry(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Arbitrator:", offer.getArbitrator().getName(), ++gridRow);
        Label arbitratorLink = new Label(offer.getArbitrator().getUrl());
        arbitratorLink.setId("label-url");
        formGridPane.add(arbitratorLink, 2, gridRow);
        arbitratorLink.setOnMouseClicked(e -> {
            try
            {
                Utils.openURL(offer.getArbitrator().getUrl());
            } catch (Exception e1)
            {
                log.warn(e1.toString());
            }
        });

        FormBuilder.addVSpacer(formGridPane, ++gridRow);
        FormBuilder.addHeaderLabel(formGridPane, "More details:", ++gridRow);
        FormBuilder.addTextField(formGridPane, "Offer ID:", offer.getUid().toString(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Account ID:", offer.getAccountID(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Messaging ID:", offer.getMessageID(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Supported languages:", Formatter.languageLocalesToString(offer.getAcceptedLanguageLocales()), ++gridRow);
        FormBuilder.addTextField(formGridPane, "Supported countries:", Formatter.countryLocalesToString(offer.getAcceptedCountryLocales()), ++gridRow);

    }

    private boolean tradeAmountValid()
    {
        double tradeAmount = Converter.stringToDouble(amountTextField.getText());
        return tradeAmount <= offer.getAmount() && tradeAmount >= offer.getMinAmount();
    }


    private void initTrade()
    {
        if (tradeAmountValid())
        {
            if (blockChainFacade.verifyEmbeddedData(offer.getAccountID()))
            {
                if (!blockChainFacade.isAccountIDBlacklisted(offer.getAccountID()))
                {
                    amountTextField.setEditable(false);

                    formGridPane.getChildren().clear();

                    int gridRow = -1;
                    FormBuilder.addHeaderLabel(formGridPane, "Trade request inited", ++gridRow, 0);

                    statusTextField = FormBuilder.addLabel(formGridPane, "Current activity:", "Request confirmation from offerer to take that offer.", ++gridRow);
                    GridPane.setColumnSpan(statusTextField, 2);
                    FormBuilder.addLabel(formGridPane, "Progress:", "", ++gridRow);
                    progressBar = new ProgressBar();
                    progressBar.setProgress(0.0);
                    progressBar.setPrefWidth(300);
                    GridPane.setFillWidth(progressBar, true);
                    formGridPane.add(progressBar, 1, gridRow);

                    FormBuilder.addLabel(formGridPane, "Status:", "", ++gridRow);
                    ProgressIndicator progressIndicator = new ProgressIndicator();
                    progressIndicator.setPrefSize(20, 20);
                    progressIndicator.setLayoutY(2);
                    Pane progressIndicatorHolder = new Pane();
                    progressIndicatorHolder.getChildren().addAll(progressIndicator);
                    formGridPane.add(progressIndicatorHolder, 1, gridRow);

                    trade.setTradeAmount(Converter.stringToDouble(amountTextField.getText()));
                    trading.sendTakeOfferRequest(trade);

                    Utils.setTimeout(SIM_DELAY, (AnimationTimer animationTimer) -> {
                        onTakeOfferRequestConfirmed();
                        progressBar.setProgress(1.0 / 3.0);
                        return null;
                    });
                }
                else
                {
                    Dialogs.create()
                            .title("Offerers account ID is blacklisted")
                            .message("Offerers account ID is blacklisted.")
                            .nativeTitleBar()
                            .lightweight()
                            .showError();
                }
            }
            else
            {
                Dialogs.create()
                        .title("Offerers account ID not valid")
                        .message("Offerers registration tx is not found in blockchain or does not match the requirements.")
                        .nativeTitleBar()
                        .lightweight()
                        .showError();
            }
        }
        else
        {
            Dialogs.create()
                    .title("Your input is not valid")
                    .message("The requested amount you entered is outside of the range of the offered amount.")
                    .nativeTitleBar()
                    .lightweight()
                    .showError();
        }
    }

    private void onTakeOfferRequestConfirmed()
    {
        trading.payOfferFee(trade);

        statusTextField.setText("Offer fee payed. Send offerer payment transaction ID for confirmation.");
        Utils.setTimeout(SIM_DELAY, (AnimationTimer animationTimer) -> {
            onOfferFeePaymentConfirmed();
            progressBar.setProgress(2.0 / 3.0);
            return null;
        });
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
        if (!walletFacade.verifyAccountRegistration(offer.getAccountID(), null, null, null, null))
        {
            Dialogs.create()
                    .title("Offerers bank account is blacklisted")
                    .message("Offerers bank account is blacklisted.")
                    .nativeTitleBar()
                    .lightweight()
                    .showError();
        }

        trading.signContract(contract);
        trading.payToDepositTx(trade);

        buildWaitBankTransfer();
    }

    private void buildWaitBankTransfer()
    {
        processStepBar.next();

        formGridPane.getChildren().clear();

        gridRow = -1;
        FormBuilder.addHeaderLabel(formGridPane, "Bank transfer", ++gridRow, 0);
        infoLabel = FormBuilder.addLabel(formGridPane, "Status:", "Wait for Bank transfer.", ++gridRow);

        Utils.setTimeout(SIM_DELAY, (AnimationTimer animationTimer) -> {
            onBankTransferInited();
            return null;
        });
    }

    private void onBankTransferInited()
    {
        int gridRow = 1;
        infoLabel.setText("Bank transfer has been inited.");
        Label label = FormBuilder.addLabel(formGridPane, "", "Check your bank account and continue when you have received the money.", ++gridRow);
        GridPane.setColumnSpan(label, 2);

        formGridPane.add(nextButton, 1, ++gridRow);
        nextButton.setText("I have received the bank transfer");
        nextButton.setOnAction(e -> releaseBTC());
    }

    private void releaseBTC()
    {
        processStepBar.next();
        trading.releaseBTC(trade);

        nextButton.setText("Close");
        nextButton.setOnAction(e -> close());

        formGridPane.getChildren().clear();
        gridRow = -1;
        FormBuilder.addHeaderLabel(formGridPane, "Trade successfully completed", ++gridRow);
        FormBuilder.addTextField(formGridPane, "You have payed:", getTotalToPay(), ++gridRow);
        FormBuilder.addTextField(formGridPane, "You have received:", getTotalToReceive(), ++gridRow);
        formGridPane.add(nextButton, 1, ++gridRow);
    }


    private void close()
    {
        walletFacade.removeRegistrationWalletListener(this);

        TabPane tabPane = ((TabPane) (rootContainer.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

        navigationController.navigateToView(NavigationController.TRADE__ORDER_BOOK, "Orderbook");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean takerIsSelling()
    {
        return offer.getDirection() == Direction.BUY;
    }

    private String getTotalToReceive()
    {
        if (takerIsSelling())
            return Formatter.formatVolume(getVolume(), offer.getCurrency());
        else
            return Formatter.formatAmount(offer.getAmount(), true, true);
    }

    private void checkIfOffererIsOnline(Node isOnlineChecker, TextField isOnlineTextField)
    {
        // mock
        Utils.setTimeout(3000, (AnimationTimer animationTimer) -> {
            offererIsOnline = Math.random() > 0.3 ? true : false;
            isOnlineTextField.setText(offererIsOnline ? "Online" : "Offline");
            formGridPane.getChildren().remove(isOnlineChecker);
            return null;
        });
    }


    private void setVolume()
    {
        totalLabel.setText(Formatter.formatVolume(getVolume(), offer.getCurrency()));
    }

    private double getVolume()
    {
        return offer.getPrice() * Converter.stringToDouble(amountTextField.getText());
    }

    private String getTotalToPay()
    {
        String result = "";
        if (takerIsSelling())
        {
            double btcValue = Converter.stringToDouble(amountTextField.getText()) + BtcFormatter.satoshiToBTC(Fees.OFFER_CREATION_FEE)/* +
                    offer.getConstraints().getCollateral() * Converter.stringToDouble(amountTextField.getText())*/;
            result = Formatter.formatAmount(btcValue, true, true);
        }
        else
        {
            double btcValue = BtcFormatter.satoshiToBTC(Fees.OFFER_CREATION_FEE) /*+ offer.getConstraints().getCollateral() * Converter.stringToDouble(amountTextField.getText())*/;
            result = Formatter.formatAmount(btcValue, true, true) + "\n" + Formatter.formatVolume(getVolume(), offer.getCurrency());
        }
        return result;
    }

}

