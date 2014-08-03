package io.bitsquare.gui.market.trade;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.components.ValidatedTextField;
import io.bitsquare.gui.popups.Popups;
import io.bitsquare.gui.util.BitSquareConverter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.gui.util.BitSquareValidator;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.Trading;
import io.bitsquare.trade.protocol.taker.ProtocolForTakerAsSeller;
import io.bitsquare.trade.protocol.taker.ProtocolForTakerAsSellerListener;
import java.math.BigInteger;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnusedParameters")
public class TakerOfferController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(TakerOfferController.class);

    private final Trading trading;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;

    private NavigationController navigationController;
    private Offer offer;
    private BigInteger requestedAmount;
    private String tradeId;
    private String depositTxId;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private Accordion accordion;
    @FXML
    private TitledPane takeOfferTitledPane, waitBankTxTitledPane, summaryTitledPane;
    @FXML
    private ValidatedTextField amountTextField;
    @FXML
    private TextField priceTextField, volumeTextField, collateralTextField, feeTextField, totalTextField, bankAccountTypeTextField, countryTextField, arbitratorsTextField,
            supportedLanguagesTextField, supportedCountriesTextField, depositTxIdTextField, summaryPaidTextField, summaryReceivedTextField, summaryFeesTextField, summaryCollateralTextField,
            summaryDepositTxIdTextField, summaryPayoutTxIdTextField;
    @FXML
    private Label infoLabel, headLineLabel;
    @FXML
    private Button takeOfferButton, receivedFiatButton;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TakerOfferController(Trading trading, WalletFacade walletFacade, MessageFacade messageFacade)
    {
        this.trading = trading;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer, BigInteger requestedAmount)
    {
        this.offer = offer;
        this.requestedAmount = requestedAmount.compareTo(BigInteger.ZERO) == 0 ? offer.getAmount() : requestedAmount;

        if (amountTextField != null)
        {
            applyData();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        accordion.setExpandedPane(takeOfferTitledPane);
    }

    public void applyData()
    {
        amountTextField.setText(BtcFormatter.formatSatoshis(requestedAmount));
        amountTextField.setPromptText(BtcFormatter.formatSatoshis(offer.getMinAmount()) + " - " + BtcFormatter.formatSatoshis(offer.getAmount()));
        priceTextField.setText(BitSquareFormatter.formatPrice(offer.getPrice()));
        applyVolume();
        applyCollateral();
        applyTotal();
        feeTextField.setText(BtcFormatter.formatSatoshis(getFee()));
        totalTextField.setText(getFormattedTotal());

        bankAccountTypeTextField.setText(offer.getBankAccountType().toString());
        countryTextField.setText(offer.getBankAccountCountry().getName());

        //todo list
        // arbitratorsTextField.setText(offer.getArbitrator().getName());

        supportedLanguagesTextField.setText(BitSquareFormatter.languageLocalesToString(offer.getAcceptedLanguageLocales()));
        supportedCountriesTextField.setText(BitSquareFormatter.countryLocalesToString(offer.getAcceptedCountries()));

        amountTextField.textProperty().addListener(e -> {
            applyVolume();
            applyCollateral();
            applyTotal();

        });
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
    // GUI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onTakeOffer()
    {
        AddressEntry addressEntry = walletFacade.getAddressInfoByTradeID(offer.getId());
        BigInteger amount = BtcFormatter.stringValueToSatoshis(amountTextField.getText());
        // TODO more validation (fee payment, blacklist,...)
        if (amountTextField.isInvalid())
        {
            Popups.openErrorPopup("Invalid input", "The requested amount you entered is not a valid amount.");
        }
        else if (BitSquareValidator.tradeAmountOutOfRange(amount, offer))
        {
            Popups.openErrorPopup("Invalid input", "The requested amount you entered is outside of the range of the offered amount.");
        }
        else if (addressEntry == null || getTotal().compareTo(walletFacade.getBalanceForAddress(addressEntry.getAddress())) > 0)
        {
            Popups.openErrorPopup("Insufficient money", "You don't have enough funds for that trade.");
        }
        else if (trading.isOfferAlreadyInTrades(offer))
        {
            Popups.openErrorPopup("Offer previously accepted", "You have that offer already taken. Open the offer section to find that trade.");
        }
        else
        {
            takeOfferButton.setDisable(true);
            amountTextField.setEditable(false);
            trading.takeOffer(amount, offer, new ProtocolForTakerAsSellerListener()
            {
                @Override
                public void onDepositTxPublished(String depositTxId)
                {
                    setDepositTxId(depositTxId);
                    accordion.setExpandedPane(waitBankTxTitledPane);
                    infoLabel.setText("Deposit transaction published by offerer.\n" +
                                              "As soon as the offerer starts the \n" +
                                              "Bank transfer, you will get informed.");
                    depositTxIdTextField.setText(depositTxId);
                }

                @Override
                public void onBankTransferInited(String tradeId)
                {
                    setTradeId(tradeId);
                    headLineLabel.setText("Bank transfer initialised");
                    infoLabel.setText("Check your bank account and continue \n" + "when you have received the money.");
                    receivedFiatButton.setDisable(false);
                }

                @Override
                public void onPayoutTxPublished(Trade trade, String payoutTxId)
                {
                    accordion.setExpandedPane(summaryTitledPane);
                    summaryPaidTextField.setText(BtcFormatter.formatSatoshis(trade.getTradeAmount()));
                    summaryReceivedTextField.setText(BitSquareFormatter.formatVolume(trade.getOffer().getPrice() * BtcFormatter.satoshiToBTC(trade.getTradeAmount())));
                    summaryFeesTextField.setText(BtcFormatter.formatSatoshis(FeePolicy.TAKE_OFFER_FEE.add(FeePolicy.TX_FEE)));
                    summaryCollateralTextField.setText(BtcFormatter.formatSatoshis(trade.getCollateralAmount()));
                    summaryDepositTxIdTextField.setText(depositTxId);
                    summaryPayoutTxIdTextField.setText(payoutTxId);
                }

                @Override
                public void onFault(Throwable throwable, ProtocolForTakerAsSeller.State state)
                {
                    log.error("Error while executing trade process at state: " + state + " / " + throwable);
                    Popups.openErrorPopup("Error while executing trade process", "Error while executing trade process at state: " + state + " / " + throwable);
                }

                @Override
                public void onWaitingForPeerResponse(ProtocolForTakerAsSeller.State state)
                {
                    log.debug("Waiting for peers response at state " + state);
                }

                @Override
                public void onCompleted(ProtocolForTakerAsSeller.State state)
                {
                    log.debug("Trade protocol completed at state " + state);
                }

                @Override
                public void onTakeOfferRequestRejected(Trade trade)
                {
                    log.error("Take offer request rejected");
                    Popups.openErrorPopup("Take offer request rejected",
                                          "Your take offer request has been rejected. It might be that the offerer got another request shortly before your request arrived.");
                }
            });
        }
    }


    @FXML
    public void onReceivedFiat()
    {
        trading.onFiatReceived(tradeId);
    }

    @FXML
    public void onClose()
    {
        TabPane tabPane = ((TabPane) (rootContainer.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

        navigationController.navigateToView(NavigationItem.ORDER_BOOK);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void applyCollateral()
    {
        collateralTextField.setText(getFormattedCollateral());
    }

    private void applyVolume()
    {
        volumeTextField.setText(getFormattedVolume());
    }

    private void applyTotal()
    {
        totalTextField.setText(getFormattedTotal());
    }

    //  formatted
    private String getFormattedVolume()
    {
        return BitSquareFormatter.formatVolume(getVolume());
    }

    private String getFormattedTotal()
    {
        return BitSquareFormatter.formatDouble(BtcFormatter.satoshiToBTC(getTotal()), 4);
    }

    private String getFormattedCollateral()
    {
        return BtcFormatter.formatSatoshis(getCollateralInSatoshis());
    }

    //  values
    private double getAmountAsDouble()
    {
        return BitSquareConverter.stringToDouble(amountTextField.getText());
    }

    private BigInteger getAmountInSatoshis()
    {
        return BtcFormatter.stringValueToSatoshis(amountTextField.getText());
    }

    private double getVolume()
    {
        return offer.getPrice() * getAmountAsDouble();
    }

    private BigInteger getFee()
    {
        return FeePolicy.TAKE_OFFER_FEE.add(FeePolicy.TX_FEE);
    }

    private BigInteger getTotal()
    {
        return getFee().add(getAmountInSatoshis()).add(getCollateralInSatoshis());
    }

    private BigInteger getCollateralInSatoshis()
    {
        double amount = BitSquareConverter.stringToDouble(amountTextField.getText());
        double resultDouble = amount * (double) offer.getCollateral() / 100.0;
        return BtcFormatter.doubleValueToSatoshis(resultDouble);
    }


    public void setTradeId(String tradeId)
    {
        this.tradeId = tradeId;
    }

    public void setDepositTxId(String depositTxId)
    {
        this.depositTxId = depositTxId;
    }
}

