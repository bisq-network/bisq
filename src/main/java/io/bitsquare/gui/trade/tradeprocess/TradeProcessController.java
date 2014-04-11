package io.bitsquare.gui.trade.tradeprocess;

import com.google.inject.Inject;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.Fees;
import io.bitsquare.gui.IChildController;
import io.bitsquare.gui.INavigationController;
import io.bitsquare.gui.components.VSpacer;
import io.bitsquare.gui.components.processbar.ProcessStepBar;
import io.bitsquare.gui.components.processbar.ProcessStepItem;
import io.bitsquare.gui.util.Colors;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.gui.util.Utils;
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

public class TradeProcessController implements Initializable, IChildController
{
    private TradingFacade tradingFacade;
    private Offer offer;
    private Trade trade;
    private Contract contract;

    private INavigationController navigationController;
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
    public TradeProcessController(TradingFacade tradingFacade)
    {
        this.tradingFacade = tradingFacade;
    }

    @Override
    public void setNavigationController(INavigationController navigationController)
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

        trade = tradingFacade.createNewTrade(offer);
        trade.setRequestedAmount(requestedAmount);
        contract = tradingFacade.createNewContract(trade);

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
        tradingFacade.sendTakeOfferRequest(trade);
        feedbackLabel.setText("Request take offer confirmation from peer.");
        Utils.setTimeout(500, (AnimationTimer animationTimer) -> {
            onTakeOfferRequestConfirmed();
            progressBar.setProgress(1.0 / 3.0);
            return null;
        });
    }

    private void onTakeOfferRequestConfirmed()
    {
        tradingFacade.payOfferFee(trade);

        feedbackLabel.setText("Request offer fee payment confirmation from peer.");
        Utils.setTimeout(500, (AnimationTimer animationTimer) -> {
            onOfferFeePaymentConfirmed();
            progressBar.setProgress(2.0 / 3.0);
            return null;
        });
    }

    private void onOfferFeePaymentConfirmed()
    {
        tradingFacade.requestOffererDetailData();
        feedbackLabel.setText("Request detail data from peer.");
        Utils.setTimeout(500, (AnimationTimer animationTimer) -> {
            onUserDetailsReceived();
            progressBar.setProgress(1.0);
            return null;
        });
    }

    private void onUserDetailsReceived()
    {
        tradingFacade.signContract(contract);
        tradingFacade.payToDepositTx(trade);

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
        nextButton.setText("Money received on Bank account");
        nextButton.setOnAction(e -> releaseBTC());
        vBox.getChildren().add(nextButton);
    }

    private void releaseBTC()
    {
        processStepBar.next();
        tradingFacade.releaseBTC(trade);

        vBox.getChildren().remove(infoLabel);

        nextButton.setText("Close");
        nextButton.setOnAction(e -> close());

        GridPane summaryGridPane = new GridPane();
        int row = 0;
        summaryGridPane.setVgap(5);
        summaryGridPane.setHgap(5);
        summaryGridPane.setPadding(new Insets(5, 5, 5, 5));

        addLabel(summaryGridPane, "You have payed:", getTotalToPay(), ++row);
        addLabel(summaryGridPane, "You have received:\n ", getTotalToReceive(), ++row);

        TitledPane summaryTitlePane = new TitledPane("Trade summary:", summaryGridPane);
        summaryTitlePane.setCollapsible(false);
        vBox.getChildren().add(2, summaryTitlePane);
    }

    private void close()
    {
        TabPane tabPane = ((TabPane) (rootContainer.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

        navigationController.navigateToView(INavigationController.TRADE__ORDER_BOOK, "Orderbook");
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

        amountTextField = addInputField(offerDetailsGridPane, "Amount (BTC):", Formatter.formatAmount(getAmount()), ++row);
        amountTextField.textProperty().addListener(e -> {
            setTotal();
            setVolume();
            setCollateral();
            totalToPayLabel.setText(getTotalToPay());
            totalToReceiveLabel.setText(getTotalToReceive());
            amountLabel.setText(amountTextField.getText());

        });

        offerDetailsGridPane.add(new Label("(" + offer.getAmount() + "BTC - " + offer.getMinAmount() + "BTC)"), 2, row);

        addLabel(offerDetailsGridPane, "Price:", Formatter.formatPriceWithCurrencyPair(offer.getPrice(), offer.getCurrency()), ++row);
        totalLabel = addLabel(offerDetailsGridPane, "Total:", "", ++row);
        setTotal();
        collateralLabel1 = addLabel(offerDetailsGridPane, "Collateral:", Formatter.formatCollateral(offer.getOfferConstraints().getCollateral(), getAmount()), ++row);
        addLabel(offerDetailsGridPane, "Offer fee:", Formatter.formatSatoshis(Fees.OFFER_CREATION_FEE, true), ++row);
        addVSpacer(offerDetailsGridPane, ++row);
        totalToPayLabel = addLabel(offerDetailsGridPane, "You pay:", getTotalToPay(), ++row);
        totalToReceiveLabel = addLabel(offerDetailsGridPane, "You receive:\n ", getTotalToReceive(), ++row);

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
        addHeaderLabel(contractGridPane, "Offer details:", row);
        addLabel(contractGridPane, "Offer ID:", offer.getUid().toString(), ++row);
        addLabel(contractGridPane, "Offer type:", Formatter.formatDirection((offer.getDirection() == Direction.BUY ? Direction.SELL : Direction.BUY), false), ++row);
        amountLabel = addLabel(contractGridPane, "Amount:", Formatter.formatAmount(getAmount()), ++row);
        volumeLabel = addLabel(contractGridPane, "Volume:", "", ++row);
        setVolume();
        addLabel(contractGridPane, "Price:", Formatter.formatPriceWithCurrencyPair(offer.getPrice(), offer.getCurrency()), ++row);
        collateralLabel2 = addLabel(contractGridPane, "Collateral:", "", ++row);
        setCollateral();
        addLabel(contractGridPane, "Language:", Formatter.formatList(offerConstraints.getLanguages()), ++row);
        addLabel(contractGridPane, "Arbitrator:", offerConstraints.getArbitrator(), ++row);
        // addLabel(contractGridPane, "Identity verification:", Formatter.formatList(offerConstraints.getIdentityVerifications()), ++row);
        addLabel(contractGridPane, "Bank transfer reference ID:", "Purchase xyz 01.04.2014", ++row);

        addVSpacer(contractGridPane, ++row);
        addHeaderLabel(contractGridPane, "Offerer data:", ++row);
        addLabel(contractGridPane, "Account ID:", offerer.getAccountID(), ++row);
        addLabel(contractGridPane, "Messaging ID:", offerer.getMessageID(), ++row);
        addLabel(contractGridPane, "Country:", offerer.getCountry(), ++row);
        offererPubKeyLabel = addLabel(contractGridPane, "Payment public key:", contract.getOffererPubKey(), ++row);
        addLabel(contractGridPane, "Bank transfer type:", offerer.getBankDetails().getBankTransferType(), ++row);
        offererAccountPrimaryID = addLabel(contractGridPane, "Bank account IBAN:", offerer.getBankDetails().getAccountPrimaryID(), ++row);
        offererAccountSecondaryIDLabel = addLabel(contractGridPane, "Bank account BIC:", offerer.getBankDetails().getAccountSecondaryID(), ++row);
        offererAccountHolderNameLabel = addLabel(contractGridPane, "Bank account holder:", offerer.getBankDetails().getAccountHolderName(), ++row);

        addVSpacer(contractGridPane, ++row);
        addHeaderLabel(contractGridPane, "Offer taker data:", ++row);
        addLabel(contractGridPane, "Account ID:", taker.getAccountID(), ++row);
        addLabel(contractGridPane, "Messaging ID:", taker.getMessageID(), ++row);
        addLabel(contractGridPane, "Country:", taker.getCountry(), ++row);
        addLabel(contractGridPane, "Payment public key:", contract.getTakerPubKey(), ++row);
        addLabel(contractGridPane, "Bank transfer type:", taker.getBankDetails().getBankTransferType(), ++row);
        addLabel(contractGridPane, "Bank account IBAN:", taker.getBankDetails().getAccountPrimaryID(), ++row);
        addLabel(contractGridPane, "Bank account BIC:", taker.getBankDetails().getAccountSecondaryID(), ++row);
        addLabel(contractGridPane, "Bank account holder:", taker.getBankDetails().getAccountHolderName(), ++row);

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

    private Label addLabel(GridPane gridPane, String title, String value, int row)
    {
        gridPane.add(new Label(title), 0, row);
        Label valueLabel = new Label(value);
        gridPane.add(valueLabel, 1, row);
        return valueLabel;
    }

    private void addHeaderLabel(GridPane gridPane, String title, int row)
    {
        Label headerLabel = new Label(title);
        headerLabel.setId("form-header-text");
        gridPane.add(headerLabel, 0, row);
    }

    private TextField addInputField(GridPane gridPane, String title, String value, int row)
    {
        gridPane.add(new Label(title), 0, row);
        TextField textField = new TextField(value);
        gridPane.add(textField, 1, row);
        return textField;
    }

    private void addVSpacer(GridPane gridPane, int row)
    {
        gridPane.add(new VSpacer(10), 0, row);
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

