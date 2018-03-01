package io.bisq.gui.main.offer.createoffer;

import io.bisq.common.GlobalSettings;
import io.bisq.common.locale.CryptoCurrency;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.user.User;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.validation.AltcoinValidator;
import io.bisq.gui.util.validation.BtcValidator;
import io.bisq.gui.util.validation.FiatPriceValidator;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import org.bitcoinj.core.Coin;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static io.bisq.core.user.PreferenceMakers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BtcWalletService .class, AddressEntry.class, PriceFeedService.class, User.class, FeeService.class, CreateOfferDataModel.class, PaymentAccount.class, BsqWalletService.class})
public class CreateOfferViewModelTest {

    private CreateOfferViewModel model;

    @Before
    public void setUp() {
        final CryptoCurrency btc = new CryptoCurrency("BTC", "bitcoin");
        GlobalSettings.setDefaultTradeCurrency(btc);
        Res.setBaseCurrencyCode(btc.getCode());
        Res.setBaseCurrencyName(btc.getName());

        final BSFormatter bsFormatter = new BSFormatter();
        final BtcValidator btcValidator = new BtcValidator(bsFormatter);
        final AltcoinValidator altcoinValidator = new AltcoinValidator();
        final FiatPriceValidator fiatPriceValidator = new FiatPriceValidator();

        FeeService feeService = mock(FeeService.class);
        AddressEntry addressEntry = mock(AddressEntry.class);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        User user = mock(User.class);
        PaymentAccount paymentAccount = mock(PaymentAccount.class);
        BsqWalletService bsqWalletService = mock(BsqWalletService.class);

        when(btcWalletService.getOrCreateAddressEntry(anyString(), any())).thenReturn(addressEntry);
        when(btcWalletService.getBalanceForAddress(any())).thenReturn(Coin.valueOf(1000L));
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());
        when(feeService.getTxFee(anyInt())).thenReturn(Coin.valueOf(1000L));
        when(user.findFirstPaymentAccountWithCurrency(any())).thenReturn(paymentAccount);
        when(user.getPaymentAccountsAsObservable()).thenReturn(FXCollections.observableSet());

        CreateOfferDataModel dataModel = new CreateOfferDataModel(null, btcWalletService, bsqWalletService, empty, user, null,null, priceFeedService,null, null, null, feeService, bsFormatter);
        dataModel.initWithData(OfferPayload.Direction.BUY, new CryptoCurrency("BTC", "bitcoin"));
        dataModel.activate();

        model = new CreateOfferViewModel(dataModel, null, fiatPriceValidator, altcoinValidator, btcValidator, null, null, null, null, priceFeedService, null, null, bsFormatter, null);
        model.activate();
    }

    @Test
    public void testSyncMinAmountWithAmountUntilChanged() {
        assertNull(model.amount.get());
        assertNull(model.minAmount.get());

        model.amount.set("0.0");
        assertEquals("0.0", model.amount.get());
        assertNull(model.minAmount.get());

        model.amount.set("0.03");

        assertEquals("0.03", model.amount.get());
        assertEquals("0.03", model.minAmount.get());

        model.amount.set("0.0312");

        assertEquals("0.0312", model.amount.get());
        assertEquals("0.0312", model.minAmount.get());

        model.minAmount.set("0.01");
        model.onFocusOutMinAmountTextField(true, false);

        assertEquals("0.01", model.minAmount.get());

        model.amount.set("0.0301");

        assertEquals("0.0301", model.amount.get());
        assertEquals("0.01", model.minAmount.get());
    }

    @Test
    public void testSyncMinAmountWithAmountWhenZeroCoinIsSet() {
        model.amount.set("0.03");

        assertEquals("0.03", model.amount.get());
        assertEquals("0.03", model.minAmount.get());

        model.minAmount.set("0.00");
        model.onFocusOutMinAmountTextField(true, false);

        model.amount.set("0.04");

        assertEquals("0.04", model.amount.get());
        assertEquals("0.04", model.minAmount.get());

    }

    @Test
    public void testSyncMinAmountWithAmountWhenSameValueIsSet() {
        model.amount.set("0.03");

        assertEquals("0.03", model.amount.get());
        assertEquals("0.03", model.minAmount.get());

        model.minAmount.set("0.03");
        model.onFocusOutMinAmountTextField(true, false);

        model.amount.set("0.04");

        assertEquals("0.04", model.amount.get());
        assertEquals("0.04", model.minAmount.get());
    }

    @Test
    public void testSyncMinAmountWithAmountWhenHigherMinAmountValueIsSet() {
        model.amount.set("0.03");

        assertEquals("0.03", model.amount.get());
        assertEquals("0.03", model.minAmount.get());

        model.minAmount.set("0.05");
        model.onFocusOutMinAmountTextField(true, false);

        assertEquals("0.05", model.amount.get());
        assertEquals("0.05", model.minAmount.get());
    }

}
