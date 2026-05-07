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

package bisq.core.trade.validation;

import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.offer.Offer;
import bisq.core.provider.price.PriceFeedService;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TradePriceValidationTest {

    @Test
    void checkTakersTradePriceAcceptsVerifiedPrice() throws Exception {
        long takersTradePrice = 50_000_000L;
        Offer offer = mock(Offer.class);
        when(offer.isUseMarketBasedPrice()).thenReturn(true);

        assertEquals(takersTradePrice, TradePriceValidation.checkTakersTradePrice(takersTradePrice,
                mock(PriceFeedService.class),
                offer));
        verify(offer).verifyTakersTradePrice(takersTradePrice);
    }

    @Test
    void checkTakersTradePriceWrapsOfferPriceValidationFailure() throws Exception {
        long takersTradePrice = 50_000_000L;
        Offer offer = mock(Offer.class);
        doThrow(new TradePriceOutOfToleranceException("price outside tolerance"))
                .when(offer)
                .verifyTakersTradePrice(takersTradePrice);

        assertThrows(RuntimeException.class, () -> TradePriceValidation.checkTakersTradePrice(takersTradePrice,
                mock(PriceFeedService.class),
                offer));
    }
}
