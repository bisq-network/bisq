package io.bitsquare.gui.market.trade;

import com.google.inject.Inject;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.currency.Bitcoin;
import io.bitsquare.currency.Fiat;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.util.BitSquareConverter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trading;
import io.bitsquare.user.Arbitrator;
import java.math.BigInteger;
import java.net.URL;
import java.util.Currency;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Accordion;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
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
    private Bitcoin requestedAmount;

    @FXML
    private Accordion accordion;
    @FXML
    private TitledPane profileTitledPane;
    @FXML
    private TextField amountTextField, priceTextField, volumeTextField, collateralTextField, feeTextField, totalTextField, bankAccountTypeTextField, countryTextField,
            arbitratorsTextField, supportedLanguagesTextField, supportedCountriesTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TakerOfferController(Trading trading, WalletFacade walletFacade, MessageFacade messageFacade)
    {
        this.trading = trading;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;


        Offer offer = new Offer("m",
                Direction.BUY,
                111,
                new BigInteger("100000000"),
                new BigInteger("10000000"),
                BankAccountType.OK_PAY,
                Currency.getInstance("EUR"),
                CountryUtil.getDefaultCountry(),
                "baid",
                new Arbitrator(),
                10,
                CountryUtil.getAllCountriesFor(CountryUtil.getAllRegions().get(0)),
                LanguageUtil.getAllLanguageLocales());

        initWithData(offer, new Bitcoin("50000000"));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer, Bitcoin requestedAmount)
    {
        this.offer = offer;
        this.requestedAmount = requestedAmount.isZero() ? new Bitcoin(offer.getAmount()) : requestedAmount;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        accordion.setExpandedPane(profileTitledPane);

        if (offer != null && requestedAmount != null)
        {
            amountTextField.setText(requestedAmount.getFormattedValue());
            amountTextField.setPromptText(new Bitcoin(offer.getMinAmount()).getFormattedValue() + " - " + new Bitcoin(offer.getAmount()).getFormattedValue());
            priceTextField.setText(new Fiat(offer.getPrice()).getFormattedValue());
            applyVolume();
            applyCollateral();
            applyTotal();
            feeTextField.setText(getFee().getFormattedValue());
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

    public void onTakeOffer()
    {
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
        return BitSquareFormatter.formatVolume(getTotal());
    }

    private String getFormattedCollateral()
    {
        return BtcFormatter.satoshiToString(getCollateralInSatoshis());
    }

    //  values
    private double getAmountAsDouble()
    {
        return BitSquareConverter.stringToDouble2(amountTextField.getText());
    }

    private double getVolume()
    {
        return offer.getPrice() * getAmountAsDouble();
    }

    private Bitcoin getFee()
    {
        return FeePolicy.TAKE_OFFER_FEE.addBitcoin(FeePolicy.TX_FEE);
    }

    private double getTotal()
    {
        return getFee().doubleValue() + getVolume();
    }

    private BigInteger getCollateralInSatoshis()
    {
        double amount = BitSquareConverter.stringToDouble2(amountTextField.getText());
        double resultDouble = amount * (double) offer.getCollateral() / 100.0;
        return BtcFormatter.doubleValueToSatoshis(resultDouble);
    }

}

