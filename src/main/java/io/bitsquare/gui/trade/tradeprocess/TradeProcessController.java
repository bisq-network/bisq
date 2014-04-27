package io.bitsquare.gui.trade.tradeprocess;

import com.google.inject.Inject;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.Fees;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.components.VSpacer;
import io.bitsquare.gui.components.processbar.ProcessStepBar;
import io.bitsquare.gui.components.processbar.ProcessStepItem;
import io.bitsquare.gui.util.*;
import io.bitsquare.trade.*;
import io.bitsquare.user.User;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.controlsfx.dialog.Dialogs;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class TradeProcessController implements Initializable, ChildController
{
    private Trading trading;
    private Offer offer;
    private Trade trade;
    private Contract contract;

    private NavigationController navigationController;
    private List<ProcessStepItem> processStepItems = new ArrayList();
    private double requestedAmount;

    private VBox vBox;
    private TitledPane offerDetailsTitlePane, contractTitlePane;
    private ProcessStepBar<String> processStepBar;
    private Button nextButton;
    private TextField amountTextField;
    private Label offererPubKeyLabel, offererAccountPrimaryID, offererAccountSecondaryIDLabel,
            offererAccountHolderNameLabel, feedbackLabel, infoLabel, totalLabel, volumeLabel, totalToPayLabel,
            totalToReceiveLabel, collateralLabel1, collateralLabel2, amountLabel;
    private Pane progressPane;
    private ProgressBar progressBar;
    private ProgressIndicator progressIndicator;


    @FXML
    public AnchorPane rootContainer;

    @Inject
    public TradeProcessController(Trading trading)
    {
        this.trading = trading;
    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
    }

    public void initView(Offer offer, double requestedAmount)
    {
        this.offer = offer;
        this.requestedAmount = requestedAmount;

        trade = trading.createNewTrade(offer);
        trade.setRequestedAmount(requestedAmount);
        contract = trading.createNewContract(trade);

        processStepItems.add(new ProcessStepItem(takerIsSelling() ? "Sell BTC" : "Buy BTC", Colors.BLUE));
        processStepItems.add(new ProcessStepItem("Bank transfer", Colors.BLUE));
        processStepItems.add(new ProcessStepItem("Completed", Colors.BLUE));
        processStepBar = new ProcessStepBar(processStepItems);

        buildStep1();
    }

    private void trade()
    {
        double requestedAmount = Converter.convertToDouble(amountTextField.getText());
        if (requestedAmount <= offer.getAmount() && requestedAmount >= offer.getMinAmount())
        {
            amountTextField.setEditable(false);
            trade.setRequestedAmount(requestedAmount);

            vBox.getChildren().remove(nextButton);
            AnchorPane.setTopAnchor(contractTitlePane, 350.0);

            progressBar = new ProgressBar();
            progressBar.setProgress(0.0);
            progressBar.setPrefWidth(200);
            progressBar.relocate(10, 10);

            progressIndicator = new ProgressIndicator();
            progressIndicator.setProgress(-1.0);
            progressIndicator.setPrefSize(20, 20);
            progressIndicator.relocate(220, 10);

            feedbackLabel = new Label();
            feedbackLabel.setPadding(new Insets(-10, 0, 0, 0));
            feedbackLabel.setId("feedback-text");
            feedbackLabel.relocate(10, 50);

            progressPane = new Pane();
            progressPane.getChildren().addAll(progressBar, progressIndicator, feedbackLabel);

            vBox.getChildren().add(progressPane);

            sendTakeOfferRequest();
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

    // Payment Process
    private void sendTakeOfferRequest()
    {
        trading.sendTakeOfferRequest(trade);
        feedbackLabel.setText("Request take offer confirmation from peer.");
        Utils.setTimeout(500, (AnimationTimer animationTimer) -> {
            onTakeOfferRequestConfirmed();
            progressBar.setProgress(1.0 / 3.0);
            return null;
        });
    }

    private void onTakeOfferRequestConfirmed()
    {
        trading.payOfferFee(trade);

        feedbackLabel.setText("Request offer fee payment confirmation from peer.");
        Utils.setTimeout(500, (AnimationTimer animationTimer) -> {
            onOfferFeePaymentConfirmed();
            progressBar.setProgress(2.0 / 3.0);
            return null;
        });
    }

    private void onOfferFeePaymentConfirmed()
    {
        trading.requestOffererDetailData();
        feedbackLabel.setText("Request detail data from peer.");
        Utils.setTimeout(500, (AnimationTimer animationTimer) -> {
            onUserDetailsReceived();
            progressBar.setProgress(1.0);
            return null;
        });
    }

    private void onUserDetailsReceived()
    {
        trading.signContract(contract);
        trading.payToDepositTx(trade);

        buildWaitBankTransfer();
    }

    private void buildWaitBankTransfer()
    {
        processStepBar.next();

        vBox.getChildren().remove(progressPane);
        vBox.getChildren().remove(offerDetailsTitlePane);
        vBox.getChildren().remove(nextButton);
        rootContainer.getChildren().remove(contractTitlePane);

        infoLabel = new Label("Wait for Bank transfer.");
        vBox.getChildren().addAll(infoLabel);

        Utils.setTimeout(2000, (AnimationTimer animationTimer) -> {
            onBankTransferInited();
            return null;
        });
    }

    private void onBankTransferInited()
    {
        infoLabel.setText("Bank transfer has been inited.\nCheck your bank account and continue when you have received the money.\n");
        nextButton.setText("I have received the bank transfer");
        nextButton.setOnAction(e -> releaseBTC());
        vBox.getChildren().add(nextButton);
    }

    private void releaseBTC()
    {
        processStepBar.next();
        trading.releaseBTC(trade);

        vBox.getChildren().remove(infoLabel);

        nextButton.setText("Close");
        nextButton.setOnAction(e -> close());

        GridPane summaryGridPane = new GridPane();
        int row = 0;
        summaryGridPane.setVgap(5);
        summaryGridPane.setHgap(5);
        summaryGridPane.setPadding(new Insets(5, 5, 5, 5));

        FormBuilder.addLabel(summaryGridPane, "You have payed:", getTotalToPay(), ++row);
        FormBuilder.addLabel(summaryGridPane, "You have received:\n ", getTotalToReceive(), ++row);

        TitledPane summaryTitlePane = new TitledPane("Trade summary:", summaryGridPane);
        summaryTitlePane.setCollapsible(false);
        vBox.getChildren().add(2, summaryTitlePane);
    }

    private void close()
    {
        TabPane tabPane = ((TabPane) (rootContainer.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

        navigationController.navigateToView(NavigationController.TRADE__ORDER_BOOK, "Orderbook");
    }

    private void buildStep1()
    {
        OfferConstraints offerConstraints = offer.getOfferConstraints();
        User taker = contract.getTaker();
        User offerer = contract.getOfferer();

        GridPane offerDetailsGridPane = new GridPane();
        int row = 0;
        offerDetailsGridPane.setVgap(5);
        offerDetailsGridPane.setHgap(5);
        offerDetailsGridPane.setPadding(new Insets(5, 5, 5, 5));

        amountTextField = FormBuilder.addInputField(offerDetailsGridPane, "Amount (BTC):", Formatter.formatAmount(getAmount()), ++row);
        amountTextField.textProperty().addListener(e -> {
            setTotal();
            setVolume();
            setCollateral();
            totalToPayLabel.setText(getTotalToPay());
            totalToReceiveLabel.setText(getTotalToReceive());
            amountLabel.setText(amountTextField.getText());

        });

        offerDetailsGridPane.add(new Label("(" + offer.getAmount() + "BTC - " + offer.getMinAmount() + "BTC)"), 2, row);

        FormBuilder.addLabel(offerDetailsGridPane, "Price:", Formatter.formatPriceWithCurrencyPair(offer.getPrice(), offer.getCurrency()), ++row);
        totalLabel = FormBuilder.addLabel(offerDetailsGridPane, "Total:", "", ++row);
        setTotal();
        collateralLabel1 = FormBuilder.addLabel(offerDetailsGridPane, "Collateral:", Formatter.formatCollateral(offer.getOfferConstraints().getCollateral(), getAmount()), ++row);
        FormBuilder.addLabel(offerDetailsGridPane, "Offer fee:", Formatter.formatSatoshis(Fees.OFFER_CREATION_FEE, true), ++row);
        FormBuilder.addVSpacer(offerDetailsGridPane, ++row);
        totalToPayLabel = FormBuilder.addLabel(offerDetailsGridPane, "You pay:", getTotalToPay(), ++row);
        totalToReceiveLabel = FormBuilder.addLabel(offerDetailsGridPane, "You receive:\n ", getTotalToReceive(), ++row);

        offerDetailsTitlePane = new TitledPane(takerIsSelling() ? "Sell Bitcoin" : "Buy Bitcoin", offerDetailsGridPane);
        offerDetailsTitlePane.setCollapsible(false);

        nextButton = new Button(processStepItems.get(0).getLabel());
        nextButton.setDefaultButton(true);
        nextButton.setOnAction(e -> trade());

        GridPane contractGridPane = new GridPane();
        contractGridPane.setVgap(5);
        contractGridPane.setHgap(5);
        contractGridPane.setPadding(new Insets(5, 5, 5, 5));
        row = 0;
        FormBuilder.addHeaderLabel(contractGridPane, "Offer details:", row);
        FormBuilder.addLabel(contractGridPane, "Offer ID:", offer.getUid().toString(), ++row);
        FormBuilder.addLabel(contractGridPane, "Offer type:", Formatter.formatDirection((offer.getDirection() == Direction.BUY ? Direction.SELL : Direction.BUY), false), ++row);
        amountLabel = FormBuilder.addLabel(contractGridPane, "Amount:", Formatter.formatAmount(getAmount()), ++row);
        volumeLabel = FormBuilder.addLabel(contractGridPane, "Volume:", "", ++row);
        setVolume();
        FormBuilder.addLabel(contractGridPane, "Price:", Formatter.formatPriceWithCurrencyPair(offer.getPrice(), offer.getCurrency()), ++row);
        collateralLabel2 = FormBuilder.addLabel(contractGridPane, "Collateral:", "", ++row);
        setCollateral();
        FormBuilder.addLabel(contractGridPane, "Language:", Formatter.formatList(offerConstraints.getLanguages()), ++row);
        FormBuilder.addLabel(contractGridPane, "Arbitrator:", offerConstraints.getArbitrator(), ++row);
        // FormBuilder.addLabel(contractGridPane, "Identity verification:", Formatter.formatList(offerConstraints.getIdentityVerifications()), ++row);
        FormBuilder.addLabel(contractGridPane, "Bank transfer reference ID:", "Purchase xyz 01.04.2014", ++row);

        FormBuilder.addVSpacer(contractGridPane, ++row);
        FormBuilder.addHeaderLabel(contractGridPane, "Offerer data:", ++row);
        FormBuilder.addLabel(contractGridPane, "Account ID:", offerer.getAccountID(), ++row);
        FormBuilder.addLabel(contractGridPane, "Messaging ID:", offerer.getMessageID(), ++row);
        FormBuilder.addLabel(contractGridPane, "Country:", offerer.getCountry(), ++row);
        offererPubKeyLabel = FormBuilder.addLabel(contractGridPane, "Payment public key:", contract.getOffererPubKey(), ++row);
        FormBuilder.addLabel(contractGridPane, "Bank transfer type:", offerer.getCurrentBankAccount().getBankAccountType().toString(), ++row);
        offererAccountPrimaryID = FormBuilder.addLabel(contractGridPane, "Bank account IBAN:", offerer.getCurrentBankAccount().getAccountPrimaryID(), ++row);
        offererAccountSecondaryIDLabel = FormBuilder.addLabel(contractGridPane, "Bank account BIC:", offerer.getCurrentBankAccount().getAccountSecondaryID(), ++row);
        offererAccountHolderNameLabel = FormBuilder.addLabel(contractGridPane, "Bank account holder:", offerer.getCurrentBankAccount().getAccountHolderName(), ++row);

        FormBuilder.addVSpacer(contractGridPane, ++row);
        FormBuilder.addHeaderLabel(contractGridPane, "Offer taker data:", ++row);
        FormBuilder.addLabel(contractGridPane, "Account ID:", taker.getAccountID(), ++row);
        FormBuilder.addLabel(contractGridPane, "Messaging ID:", taker.getMessageID(), ++row);
        FormBuilder.addLabel(contractGridPane, "Country:", taker.getCountry(), ++row);
        FormBuilder.addLabel(contractGridPane, "Payment public key:", contract.getTakerPubKey(), ++row);
        FormBuilder.addLabel(contractGridPane, "Bank transfer type:", taker.getCurrentBankAccount().getBankAccountType().toString(), ++row);
        FormBuilder.addLabel(contractGridPane, "Bank account IBAN:", taker.getCurrentBankAccount().getAccountPrimaryID(), ++row);
        FormBuilder.addLabel(contractGridPane, "Bank account BIC:", taker.getCurrentBankAccount().getAccountSecondaryID(), ++row);
        FormBuilder.addLabel(contractGridPane, "Bank account holder:", taker.getCurrentBankAccount().getAccountHolderName(), ++row);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(contractGridPane);

        contractTitlePane = new TitledPane("Contract", scrollPane);
        contractTitlePane.setCollapsible(false);
        AnchorPane.setLeftAnchor(contractTitlePane, 10.0);
        AnchorPane.setRightAnchor(contractTitlePane, 10.0);
        AnchorPane.setTopAnchor(contractTitlePane, 324.0);
        AnchorPane.setBottomAnchor(contractTitlePane, 10.0);

        vBox = new VBox();
        AnchorPane.setLeftAnchor(vBox, 10.0);
        AnchorPane.setRightAnchor(vBox, 10.0);
        AnchorPane.setTopAnchor(vBox, 10.0);
        vBox.setSpacing(10);

        vBox.getChildren().addAll(processStepBar, new VSpacer(5), offerDetailsTitlePane, nextButton);
        rootContainer.getChildren().addAll(vBox, contractTitlePane);
    }

    private void setTotal()
    {
        totalLabel.setText(Formatter.formatVolume(getVolume()));
    }

    private void setVolume()
    {
        totalLabel.setText(Formatter.formatVolume(getVolume(), offer.getCurrency()));
    }

    private boolean takerIsSelling()
    {
        return offer.getDirection() == Direction.BUY;
    }

    private double getVolume()
    {
        return offer.getPrice() * Converter.convertToDouble(amountTextField.getText());
    }

    private double getAmount()
    {
        return requestedAmount > 0 ? requestedAmount : offer.getAmount();
    }

    private String getTotalToPay()
    {
        String result = "";
        if (takerIsSelling())
        {
            double btcValue = Converter.convertToDouble(amountTextField.getText()) + BtcFormatter.satoshiToBTC(Fees.OFFER_CREATION_FEE) +
                    offer.getOfferConstraints().getCollateral() * Converter.convertToDouble(amountTextField.getText());
            result = Formatter.formatAmount(btcValue, true, true);
        }
        else
        {
            double btcValue = BtcFormatter.satoshiToBTC(Fees.OFFER_CREATION_FEE) + offer.getOfferConstraints().getCollateral() * Converter.convertToDouble(amountTextField.getText());
            result = Formatter.formatAmount(btcValue, true, true) + "\n" + Formatter.formatVolume(getVolume(), offer.getCurrency());
        }
        return result;
    }

    private String getTotalToReceive()
    {
        String result = "";
        if (takerIsSelling())
        {
            double btcValue = offer.getOfferConstraints().getCollateral() * Converter.convertToDouble(amountTextField.getText());
            result = Formatter.formatAmount(btcValue, true, true) + "\n" + Formatter.formatVolume(getVolume(), offer.getCurrency());
        }
        else
        {
            double btcValue = Converter.convertToDouble(amountTextField.getText()) +
                    offer.getOfferConstraints().getCollateral() * Converter.convertToDouble(amountTextField.getText());
            result = Formatter.formatAmount(btcValue, true, true);
        }
        return result;
    }

    public void setCollateral()
    {
        String value = Formatter.formatCollateral(offer.getOfferConstraints().getCollateral(), Converter.convertToDouble(amountTextField.getText()));
        collateralLabel1.setText(value);
        collateralLabel2.setText(value);
    }
}

