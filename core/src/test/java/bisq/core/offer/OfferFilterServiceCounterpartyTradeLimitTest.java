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

package bisq.core.offer;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.filter.FilterPolicyService;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// A taker checks the trade limit of the maker of a fiat offer by the maker's account age witness. Without the witness
// only an offer of at most 0.002 BTC passes. The age cannot lower the limit of a stablecoin payment method.
class OfferFilterServiceCounterpartyTradeLimitTest {
    // The mock verifies no amount, as if the witness of the maker had not reached us
    private final AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
    private final OfferFilterService offerFilterService = new OfferFilterService(null,
            mock(Preferences.class),
            mock(FilterPolicyService.class),
            accountAgeWitnessService);

    @Test
    void offerOfAStableCoinPaymentMethodNeedsNoWitnessOfTheMaker() {
        Offer offer = usdOffer("USDT_TRC20");

        assertFalse(offerFilterService.isInsufficientCounterpartyTradeLimit(offer));
        verify(accountAgeWitnessService, never()).verifyPeersTradeAmount(any(), any(), any());
    }

    // The same offer with a payment method with chargeback risk is held back until the witness is known
    @Test
    void offerOfAnotherFiatPaymentMethodNeedsTheWitnessOfTheMaker() {
        Offer offer = usdOffer(PaymentMethod.CLEAR_X_CHANGE_ID);

        assertTrue(offerFilterService.isInsufficientCounterpartyTradeLimit(offer));
    }

    private static Offer usdOffer(String paymentMethodId) {
        Offer offer = mock(Offer.class);
        OfferPayloadBase offerPayloadBase = mock(OfferPayloadBase.class);
        when(offerPayloadBase.getHash()).thenReturn(paymentMethodId.getBytes());
        when(offer.getOfferPayloadBase()).thenReturn(offerPayloadBase);
        when(offer.isFiatOffer()).thenReturn(true);
        when(offer.getCurrencyCode()).thenReturn("USD");
        // From the list of payment methods, as another test replaces some static fields of PaymentMethod by mocks
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.getPaymentMethod(paymentMethodId));
        when(offer.getAmount()).thenReturn(Coin.parseCoin("0.1"));
        return offer;
    }
}
