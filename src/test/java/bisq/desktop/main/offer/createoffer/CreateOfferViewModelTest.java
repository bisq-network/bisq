/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.offer.createoffer;

import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.validation.AltcoinValidator;
import bisq.desktop.util.validation.BtcValidator;
import bisq.desktop.util.validation.FiatPriceValidator;
import bisq.desktop.util.validation.InputValidator;
import bisq.desktop.util.validation.SecurityDepositValidator;

import bisq.core.btc.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.User;

import bisq.common.GlobalSettings;
import bisq.common.locale.CryptoCurrency;
import bisq.common.locale.Res;

import org.bitcoinj.core.Coin;

import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.FXCollections;

import java.time.Instant;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static bisq.core.user.PreferenceMakers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BtcWalletService.class, AddressEntry.class, PriceFeedService.class, User.class,
        FeeService.class, CreateOfferDataModel.class, PaymentAccount.class, BsqWalletService.class,
        SecurityDepositValidator.class})
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
        SecurityDepositValidator securityDepositValidator = mock(SecurityDepositValidator.class);

        when(btcWalletService.getOrCreateAddressEntry(anyString(), any())).thenReturn(addressEntry);
        when(btcWalletService.getBalanceForAddress(any())).thenReturn(Coin.valueOf(1000L));
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());
        when(priceFeedService.getMarketPrice(anyString())).thenReturn(new MarketPrice("USD", 12684.0450, Instant.now().getEpochSecond(), true));
        when(feeService.getTxFee(anyInt())).thenReturn(Coin.valueOf(1000L));
        when(user.findFirstPaymentAccountWithCurrency(any())).thenReturn(paymentAccount);
        when(user.getPaymentAccountsAsObservable()).thenReturn(FXCollections.observableSet());
        when(securityDepositValidator.validate(any())).thenReturn(new InputValidator.ValidationResult(false));

        CreateOfferDataModel dataModel = new CreateOfferDataModel(null, btcWalletService, bsqWalletService, empty, user, null, null, priceFeedService, null, null, null, feeService, bsFormatter);
        dataModel.initWithData(OfferPayload.Direction.BUY, new CryptoCurrency("BTC", "bitcoin"));
        dataModel.activate();

        model = new CreateOfferViewModel(dataModel, null, fiatPriceValidator, altcoinValidator, btcValidator, null, securityDepositValidator, null, null, priceFeedService, null, null, bsFormatter, null);
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

    @Test
    public void testSyncPriceMarginWithVolumeAndFixedPrice() {
        model.amount.set("0.01");
        model.onFocusOutPriceAsPercentageTextField(true, false); //leave focus without changing
        assertEquals("0.00", model.marketPriceMargin.get());
        assertEquals("0.00000078", model.volume.get());
        assertEquals("12684.04500000", model.price.get());
    }
}
