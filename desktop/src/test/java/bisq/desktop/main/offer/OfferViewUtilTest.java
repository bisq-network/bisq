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

package bisq.desktop.main.offer;

import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.availability.AvailabilityResult;
import bisq.core.offer.bisq_v1.OfferPayload;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

public class OfferViewUtilTest {

    @BeforeEach
    public void setUp() {
        Locale.setDefault(Locale.of("en", "US"));
        GlobalSettings.setLocale(Locale.of("en", "US"));
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void everyRefusalHasItsOwnText() {
        for (AvailabilityResult result : AvailabilityResult.values()) {
            if (result == AvailabilityResult.AVAILABLE || result == AvailabilityResult.OFFER_TAKEN)
                continue;
            String key = "takeOffer.failed.availabilityResult." + result.name();

            // Res returns the key itself when the text is missing
            assertNotEquals(key, OfferViewUtil.getNotAvailableWarning(result, false));
        }
    }

    @Test
    public void onlyRefusalsAndTimeoutsAreReportedByTheOfferState() {
        Offer offer = new Offer(mock(OfferPayload.class));
        for (Offer.State state : Offer.State.values()) {
            offer.setState(state);
            boolean expected = state == Offer.State.NOT_AVAILABLE || state == Offer.State.MAKER_OFFLINE;
            assertEquals(expected, OfferViewUtil.isReportedByOfferState(offer), state.name());
        }
    }
}
