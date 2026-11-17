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

package bisq.core.api;

import bisq.core.api.exception.NotAvailableException;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.FiatCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.bisq_v1.TakeOfferModel;
import bisq.core.offer.bsq_swap.BsqSwapTakeOfferModel;
import bisq.core.payment.CustomPaymentMethodAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.ClosedTradableFormatter;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.user.User;

import org.bitcoinj.core.Coin;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomPaymentMethodApiTest {
    private static final String ACCOUNT_ID = "accountId";
    private static final long AMOUNT = 100_000;

    private CoreTradesService coreTradesService;
    private TakeOfferModel takeOfferModel;
    private TradeManager tradeManager;
    private User user;

    @BeforeEach
    void setUp() {
        takeOfferModel = mock(TakeOfferModel.class);
        tradeManager = mock(TradeManager.class);
        user = mock(User.class);
        coreTradesService = new CoreTradesService(new CoreContext(),
                mock(CoreWalletsService.class),
                mock(BtcWalletService.class),
                mock(OfferUtil.class),
                mock(BsqSwapTradeManager.class),
                mock(ClosedTradableManager.class),
                mock(ClosedTradableFormatter.class),
                mock(FailedTradesManager.class),
                takeOfferModel,
                mock(BsqSwapTakeOfferModel.class),
                tradeManager,
                mock(TradeUtil.class),
                user);
    }

    // The API does not create an account of the custom payment method, so it does not list the payment method
    @Test
    void fiatPaymentMethodsLeaveOutTheCustomPaymentMethod() {
        CorePaymentAccountsService service = new CorePaymentAccountsService(null, null, null, null);

        List<PaymentMethod> paymentMethods = service.getFiatPaymentMethods();

        assertTrue(paymentMethods.stream().noneMatch(PaymentMethod::isCustom));
        assertTrue(paymentMethods.stream().anyMatch(paymentMethod -> paymentMethod.getId().equals(PaymentMethod.SEPA_ID)));
        assertTrue(paymentMethods.stream().anyMatch(paymentMethod -> paymentMethod.getId().equals(PaymentMethod.F2F_ID)));
    }

    // An account of another custom payment method has the payment method ID of the offer. The peer would refuse
    // it when it is revealed, after both traders have paid their trade fee.
    @Test
    void takeOfferRefusesAnAccountOfAnotherCustomPaymentMethod() {
        when(user.getPaymentAccount(ACCOUNT_ID)).thenReturn(account("Airtel Money", "Jane Doe, +254 700 000000"));
        Offer offer = offer("M-Pesa");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> takeOffer(offer));

        assertEquals("payment account with id 'accountId' does not match offer with id 'offerId'",
                exception.getMessage());
        verifyNoInteractions(takeOfferModel, tradeManager);
    }

    // An account which was imported has not passed the account form
    @Test
    void takeOfferRefusesAnAccountWithoutValidAccountData() {
        when(user.getPaymentAccount(ACCOUNT_ID)).thenReturn(account("M-Pesa", ""));
        Offer offer = offer("M-Pesa");

        assertThrows(IllegalArgumentException.class, () -> takeOffer(offer));

        verifyNoInteractions(takeOfferModel, tradeManager);
    }

    // Shows that the tests above fail for the account: with an account of the same name the next step is reached,
    // which fails as the wallet of the mock has no funds
    @Test
    void takeOfferUsesAnAccountWithTheNameOfTheOffer() {
        CustomPaymentMethodAccount account = account("m pesa", "Jane Doe, +254 700 000000");
        when(user.getPaymentAccount(ACCOUNT_ID)).thenReturn(account);
        Offer offer = offer("M-Pesa");

        assertThrows(NotAvailableException.class, () -> takeOffer(offer));

        verify(takeOfferModel).initModel(offer, account, AMOUNT, true);
        verifyNoInteractions(tradeManager);
    }

    private void takeOffer(Offer offer) {
        coreTradesService.takeOffer(offer, ACCOUNT_ID, "BTC", AMOUNT, trade -> {
        }, errorMessage -> {
        });
    }

    private static CustomPaymentMethodAccount account(String paymentMethodName, String accountData) {
        CustomPaymentMethodAccount account = new CustomPaymentMethodAccount();
        account.init();
        account.setPaymentMethodName(paymentMethodName);
        account.setAccountData(accountData);
        account.setSingleTradeCurrency(new FiatCurrency("KES"));
        return account;
    }

    private static Offer offer(String customPaymentMethodName) {
        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn("offerId");
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.getPaymentMethod(PaymentMethod.CUSTOM_ID));
        when(offer.getCustomPaymentMethodName()).thenReturn(customPaymentMethodName);
        when(offer.getCurrencyCode()).thenReturn("KES");
        when(offer.getMinAmount()).thenReturn(Coin.valueOf(AMOUNT));
        when(offer.getAmount()).thenReturn(Coin.valueOf(AMOUNT));
        return offer;
    }
}
