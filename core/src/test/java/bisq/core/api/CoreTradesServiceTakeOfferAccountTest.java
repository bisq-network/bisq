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
import bisq.core.offer.Offer;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.bisq_v1.TakeOfferModel;
import bisq.core.offer.bsq_swap.BsqSwapTakeOfferModel;
import bisq.core.payment.FasterPaymentsAccount;
import bisq.core.payment.HalCashAccount;
import bisq.core.payment.MoneyBeamAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.ClosedTradableFormatter;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.user.User;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The API gets the account of the taker as an ID. It must match the offer, as the accounts do which the desktop
// offers to the taker.
class CoreTradesServiceTakeOfferAccountTest {
    private static final String ACCOUNT_ID = "accountId";
    private static final long AMOUNT = 100_000;

    private CoreTradesService coreTradesService;
    private OfferUtil offerUtil;
    private TakeOfferModel takeOfferModel;
    private TradeManager tradeManager;
    private User user;

    @BeforeEach
    void setUp() {
        offerUtil = mock(OfferUtil.class);
        takeOfferModel = mock(TakeOfferModel.class);
        tradeManager = mock(TradeManager.class);
        user = mock(User.class);
        coreTradesService = new CoreTradesService(new CoreContext(),
                mock(CoreWalletsService.class),
                mock(BtcWalletService.class),
                offerUtil,
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

    @Test
    void accountOfAnotherPaymentMethodIsRefused() {
        when(user.getPaymentAccount(ACCOUNT_ID)).thenReturn(new HalCashAccount());
        Offer offer = offer(PaymentMethod.MONEY_BEAM_ID, "EUR");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> takeOffer(offer));

        assertEquals("payment account with id 'accountId' does not match offer with id 'offerId'",
                exception.getMessage());
        // A refused request does not change the fee currency of the user
        verifyNoInteractions(offerUtil, takeOfferModel, tradeManager);
    }

    @Test
    void accountOfAnotherCurrencyIsRefused() {
        when(user.getPaymentAccount(ACCOUNT_ID)).thenReturn(new FasterPaymentsAccount());
        Offer offer = offer(PaymentMethod.FASTER_PAYMENTS_ID, "EUR");

        assertThrows(IllegalArgumentException.class, () -> takeOffer(offer));

        verifyNoInteractions(takeOfferModel, tradeManager);
    }

    // Shows that the tests above fail for the account: with a matching account the next step is reached, which
    // fails as the wallet of the mock has no funds
    @Test
    void matchingAccountIsUsed() {
        PaymentAccount account = new MoneyBeamAccount();
        when(user.getPaymentAccount(ACCOUNT_ID)).thenReturn(account);
        Offer offer = offer(PaymentMethod.MONEY_BEAM_ID, "EUR");

        assertThrows(NotAvailableException.class, () -> takeOffer(offer));

        verify(offerUtil).maybeSetFeePaymentCurrencyPreference("BTC");
        verify(takeOfferModel).initModel(offer, account, AMOUNT, true);
        verifyNoInteractions(tradeManager);
    }

    private void takeOffer(Offer offer) {
        coreTradesService.takeOffer(offer, ACCOUNT_ID, "BTC", AMOUNT, trade -> {
        }, errorMessage -> {
        });
    }

    private static Offer offer(String paymentMethodId, String currencyCode) {
        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn("offerId");
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.getPaymentMethod(paymentMethodId));
        when(offer.getCurrencyCode()).thenReturn(currencyCode);
        when(offer.getMinAmount()).thenReturn(Coin.valueOf(AMOUNT));
        when(offer.getAmount()).thenReturn(Coin.valueOf(AMOUNT));
        return offer;
    }
}
