package bisq.desktop.main.portfolio.editoffer;

import bisq.desktop.util.validation.SecurityDepositValidator;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Country;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOffer;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;
import bisq.core.util.validation.InputValidator;

import org.bitcoinj.core.Coin;

import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.FXCollections;

import java.time.Instant;

import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static bisq.desktop.maker.OfferMaker.btcBCHCOffer;
import static bisq.desktop.maker.PreferenceMakers.empty;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BtcWalletService.class, AddressEntry.class, PriceFeedService.class, User.class,
        FeeService.class, PaymentAccount.class, BsqWalletService.class,
        SecurityDepositValidator.class, AccountAgeWitnessService.class, BsqFormatter.class, Preferences.class,
        BsqWalletService.class})
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class EditOfferDataModelTest {

    private EditOfferDataModel model;
    private User user;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() {

        final CryptoCurrency btc = new CryptoCurrency("BTC", "bitcoin");
        GlobalSettings.setDefaultTradeCurrency(btc);
        Res.setup();

        final BSFormatter bsFormatter = new BSFormatter();

        FeeService feeService = mock(FeeService.class);
        AddressEntry addressEntry = mock(AddressEntry.class);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        user = mock(User.class);
        PaymentAccount paymentAccount = mock(PaymentAccount.class);
        Preferences preferences = mock(Preferences.class);
        BsqFormatter bsqFormatter = mock(BsqFormatter.class);
        BsqWalletService bsqWalletService = mock(BsqWalletService.class);
        SecurityDepositValidator securityDepositValidator = mock(SecurityDepositValidator.class);
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);

        when(btcWalletService.getOrCreateAddressEntry(anyString(), any())).thenReturn(addressEntry);
        when(btcWalletService.getBalanceForAddress(any())).thenReturn(Coin.valueOf(1000L));
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());
        when(priceFeedService.getMarketPrice(anyString())).thenReturn(new MarketPrice("USD", 12684.0450, Instant.now().getEpochSecond(), true));
        when(feeService.getTxFee(anyInt())).thenReturn(Coin.valueOf(1000L));
        when(user.findFirstPaymentAccountWithCurrency(any())).thenReturn(paymentAccount);
        when(user.getPaymentAccountsAsObservable()).thenReturn(FXCollections.observableSet());
        when(securityDepositValidator.validate(any())).thenReturn(new InputValidator.ValidationResult(false));
        when(accountAgeWitnessService.getMyTradeLimit(any(), any())).thenReturn(100000000L);
        when(preferences.getUserCountry()).thenReturn(new Country("US", "United States", null));
        when(bsqFormatter.formatCoin(any())).thenReturn("0");
        when(bsqWalletService.getAvailableConfirmedBalance()).thenReturn(Coin.ZERO);

        model = new EditOfferDataModel(null,
                btcWalletService, bsqWalletService, empty, user,
                null, null, priceFeedService, null,
                accountAgeWitnessService, feeService, null, null,
                null, null);
    }

    @Test
    public void testEditOfferOfRemovedAsset() {

        final CryptoCurrencyAccount bitcoinClashicAccount = new CryptoCurrencyAccount();
        bitcoinClashicAccount.setId("BCHC");

        when(user.getPaymentAccount(anyString())).thenReturn(bitcoinClashicAccount);

        model.applyOpenOffer(new OpenOffer(make(btcBCHCOffer), null));
        assertNull(model.getPreselectedPaymentAccount());
    }

    @Test
    public void testInitializeEditOfferWithRemovedAsset() {
        exception.expect(IllegalArgumentException.class);
        model.initWithData(OfferPayload.Direction.BUY, null);
    }
}
