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
import bisq.core.locale.Res;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.user.UserPayload;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static bisq.core.testutil.UserFixtures.revolutAccount;
import static bisq.core.testutil.UserFixtures.userReading;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferFilterServiceTradeLimitCacheTest {
    // TradeManager takes OfferFilterService, so it is built while the persisted data hosts are
    // collected, before User.readPersisted. A payment account added later must still reset the cached
    // trade limit results.
    @Test
    void anAccountAddedAfterAStartupBeforeInitResetsTheTradeLimitCache() {
        Res.setup();
        User user = userReading(new UserPayload());
        // Trade limit 0 for every account
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        OfferFilterService offerFilterService = new OfferFilterService(user, mock(Preferences.class),
                mock(FilterPolicyService.class), accountAgeWitnessService);
        user.readPersisted(() -> {
        });
        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn("offerId");
        when(offer.getCurrencyCode()).thenReturn("EUR");
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.REVOLUT);
        when(offer.getMirroredDirection()).thenReturn(OfferDirection.BUY);
        when(offer.getMinAmount()).thenReturn(Coin.valueOf(100_000));
        // No account for the offer yet, so its trade limit is not insufficient
        assertFalse(offerFilterService.isMyInsufficientTradeLimit(offer));

        user.addPaymentAccount(revolutAccount());

        assertTrue(offerFilterService.isMyInsufficientTradeLimit(offer));
    }
}
