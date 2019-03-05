package bisq.desktop.main.offer.createoffer;

import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferUtil;
import bisq.core.payment.ClearXchangeAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.RevolutAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import org.bitcoinj.core.Coin;

import java.util.HashSet;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



import org.mockito.BDDMockito;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BtcWalletService.class, AddressEntry.class, Preferences.class, User.class,
        PriceFeedService.class, OfferUtil.class, FeeService.class, TxFeeEstimationService.class})
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class CreateOfferDataModelTest {

    private CreateOfferDataModel model;
    private User user;
    private Preferences preferences;

    @Before
    public void setUp() {
        final CryptoCurrency btc = new CryptoCurrency("BTC", "bitcoin");
        GlobalSettings.setDefaultTradeCurrency(btc);
        Res.setup();

        AddressEntry addressEntry = mock(AddressEntry.class);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        FeeService feeService = mock(FeeService.class);
        TxFeeEstimationService feeEstimationService = mock(TxFeeEstimationService.class);
        preferences = mock(Preferences.class);
        user = mock(User.class);

        when(btcWalletService.getOrCreateAddressEntry(anyString(), any())).thenReturn(addressEntry);
        when(preferences.isUsePercentageBasedPrice()).thenReturn(true);
        when(preferences.getBuyerSecurityDepositAsPercent()).thenReturn(0.01);

        model = new CreateOfferDataModel(null, btcWalletService,
                null, preferences, user, null,
                null, priceFeedService, null,
                null, feeService, feeEstimationService,
                null, null);
    }

    @Test
    public void testUseTradeCurrencySetInOfferViewWhenInPaymentAccountAvailable() {
        final HashSet<PaymentAccount> paymentAccounts = new HashSet<>();
        final ClearXchangeAccount zelleAccount = new ClearXchangeAccount();
        zelleAccount.setId("234");
        paymentAccounts.add(zelleAccount);
        final RevolutAccount revolutAccount = new RevolutAccount();
        revolutAccount.setId("123");
        revolutAccount.setSingleTradeCurrency(new FiatCurrency("EUR"));
        revolutAccount.addCurrency(new FiatCurrency("USD"));
        paymentAccounts.add(revolutAccount);

        when(user.getPaymentAccounts()).thenReturn(paymentAccounts);
        when(preferences.getSelectedPaymentAccountForCreateOffer()).thenReturn(revolutAccount);
        PowerMockito.mockStatic(OfferUtil.class);
        BDDMockito.given(OfferUtil.getMakerFee(any(), any(), any())).willReturn(Coin.ZERO);

        model.initWithData(OfferPayload.Direction.BUY, new FiatCurrency("USD"));
        assertEquals("USD", model.getTradeCurrencyCode().get());
    }

    @Test
    public void testUseTradeAccountThatMatchesTradeCurrencySetInOffer() {
        final HashSet<PaymentAccount> paymentAccounts = new HashSet<>();
        final ClearXchangeAccount zelleAccount = new ClearXchangeAccount();
        zelleAccount.setId("234");
        paymentAccounts.add(zelleAccount);
        final RevolutAccount revolutAccount = new RevolutAccount();
        revolutAccount.setId("123");
        revolutAccount.setSingleTradeCurrency(new FiatCurrency("EUR"));
        paymentAccounts.add(revolutAccount);

        when(user.getPaymentAccounts()).thenReturn(paymentAccounts);
        when(user.findFirstPaymentAccountWithCurrency(new FiatCurrency("USD"))).thenReturn(zelleAccount);
        when(preferences.getSelectedPaymentAccountForCreateOffer()).thenReturn(revolutAccount);
        PowerMockito.mockStatic(OfferUtil.class);
        BDDMockito.given(OfferUtil.getMakerFee(any(), any(), any())).willReturn(Coin.ZERO);

        model.initWithData(OfferPayload.Direction.BUY, new FiatCurrency("USD"));
        assertEquals("USD", model.getTradeCurrencyCode().get());
    }

}
