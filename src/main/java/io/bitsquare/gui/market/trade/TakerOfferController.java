package io.bitsquare.gui.market.trade;

import com.google.bitcoin.core.Coin;
import io.bitsquare.btc.AddressEntry;
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
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.protocol.taker.ProtocolForTakerAsSeller;
import io.bitsquare.trade.protocol.taker.ProtocolForTakerAsSellerListener;
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

    private final TradeManager tradeManager;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;


    private NavigationController navigationController;
    private Offer offer;
    private Coin requestedAmount;
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
    private Label infoLabel, headLineLabel, collateralLabel;
    @FXML
    private Button takeOfferButton, receivedFiatButton;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TakerOfferController(TradeManager tradeManager, WalletFacade walletFacade, MessageFacade messageFacade)
    {
        this.tradeManager = tradeManager;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer, Coin requestedAmount)
    {
        this.offer = offer;
        this.requestedAmount = requestedAmount.compareTo(Coin.ZERO) == 0 ? offer.getAmount() : requestedAmount;

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
        amountTextField.setText(requestedAmount.toPlainString());
        amountTextField.setPromptText(BitSquareFormatter.formatCoinToBtcWithCode(offer.getMinAmount()) + " - " + BitSquareFormatter.formatCoinToBtcWithCode(offer.getAmount()));
        priceTextField.setText(BitSquareFormatter.formatPrice(offer.getPrice()));
        applyVolume();
        collateralLabel.setText("Collateral (" + getCollateralAsPercent() + "):");
        applyCollateral();
        applyTotal();
        feeTextField.setText(BitSquareFormatter.formatCoinToBtcWithCode(getFee()));
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
        Coin amount = BitSquareFormatter.parseBtcToCoin(getAmountString());
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
        else if (tradeManager.isOfferAlreadyInTrades(offer))
        {
            Popups.openErrorPopup("Offer previously accepted", "You have that offer already taken. Open the offer section to find that trade.");
        }
        else
        {
            takeOfferButton.setDisable(true);
            amountTextField.setEditable(false);
            tradeManager.takeOffer(amount, offer, new ProtocolForTakerAsSellerListener()
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

                    summaryPaidTextField.setText(BitSquareFormatter.formatCoinToBtcWithCode(trade.getTradeAmount()));
                    summaryReceivedTextField.setText(BitSquareFormatter.formatVolume(trade.getTradeVolume()));
                    summaryFeesTextField.setText(BitSquareFormatter.formatCoinToBtcWithCode(FeePolicy.TAKE_OFFER_FEE.add(FeePolicy.TX_FEE)));
                    summaryCollateralTextField.setText(BitSquareFormatter.formatCoinToBtcWithCode(trade.getCollateralAmount()));
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
        tradeManager.onFiatReceived(tradeId);
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
        collateralTextField.setText(getFormattedCollateralAsBtc());
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
        return BitSquareFormatter.formatCoinToBtcWithCode(getTotal());
    }


    //  values
    private double getAmountAsDouble()
    {
        return BitSquareConverter.stringToDouble(getAmountString());
    }

    private Coin getAmountInSatoshis()
    {
        return BitSquareFormatter.parseBtcToCoin(getAmountString());
    }

    private String getAmountString()
    {
        try
        {
            BitSquareValidator.textFieldsHasPositiveDoubleValueWithReset(amountTextField);
            return amountTextField.getText();
        } catch (BitSquareValidator.ValidationException e)
        {
            return "0";
        }
    }

    private double getVolume()
    {
        return offer.getPrice() * getAmountAsDouble();
    }

    private Coin getFee()
    {
        return FeePolicy.TAKE_OFFER_FEE.add(FeePolicy.TX_FEE);
    }

    private Coin getTotal()
    {
        return getFee().add(getAmountInSatoshis()).add(getCollateralAsCoin());
    }

    private Coin getCollateralAsCoin()
    {
        Coin amountAsCoin = BitSquareFormatter.parseBtcToCoin(getAmountString());
        return amountAsCoin.divide((long) (1d / offer.getCollateral()));
    }

    private String getFormattedCollateralAsBtc()
    {
        Coin amountAsCoin = BitSquareFormatter.parseBtcToCoin(getAmountString());
        Coin collateralAsCoin = amountAsCoin.divide((long) (1d / getCollateral()));
        return BitSquareFormatter.formatCoinToBtc(collateralAsCoin);
    }

    private String getCollateralAsPercent()
    {
        return BitSquareFormatter.formatCollateralPercent(getCollateral());
    }

    private double getCollateral()
    {
        // TODO
        return offer.getCollateral();
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

