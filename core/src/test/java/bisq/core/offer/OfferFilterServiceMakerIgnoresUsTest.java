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
import bisq.core.offer.availability.AvailabilityResult;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OfferFilterServiceMakerIgnoresUsTest {
    private long now;
    private OfferFilterService service;
    private final Offer offerOfMaker = offerFrom("maker.onion:9999");
    private final Offer otherOfferOfMaker = offerFrom("maker.onion:9999");
    private final Offer offerOfOther = offerFrom("other.onion:9999");

    @BeforeEach
    public void setUp() {
        now = 1_000_000L;
        service = new OfferFilterService(null, mock(Preferences.class), mock(FilterPolicyService.class),
                mock(AccountAgeWitnessService.class)) {
            @Override
            protected long now() {
                return now;
            }
        };
    }

    @Test
    public void aRefusalOnOneOfferClosesAllOffersOfTheMaker() {
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);

        assertTrue(service.isIgnoredByMaker(offerOfMaker));
        assertTrue(service.isIgnoredByMaker(otherOfferOfMaker));
        assertFalse(service.isIgnoredByMaker(offerOfOther));
    }

    @Test
    public void anAcceptanceReopensTheMaker() {
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);
        service.onAvailabilityAnswer(otherOfferOfMaker, AvailabilityResult.AVAILABLE);

        assertFalse(service.isIgnoredByMaker(offerOfMaker));
    }

    @Test
    public void otherAnswersAreAboutThatRequestOnly() {
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.PRICE_OUT_OF_TOLERANCE);
        assertFalse(service.isIgnoredByMaker(offerOfMaker));

        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.UNCONF_TX_LIMIT_HIT);
        assertTrue(service.isIgnoredByMaker(offerOfMaker));

        service.onAvailabilityAnswer(offerOfMaker, null);
        assertTrue(service.isIgnoredByMaker(offerOfMaker));
    }

    @Test
    public void theMakerIsAskedAgainAfterTheTtl() {
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);

        now += OfferFilterService.MAKER_IGNORES_US_TTL_MS;
        assertTrue(service.isIgnoredByMaker(offerOfMaker));
        now += 1;
        assertFalse(service.isIgnoredByMaker(offerOfMaker));
    }

    @Test
    public void aFreshRefusalRestartsTheTtl() {
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);
        now += OfferFilterService.MAKER_IGNORES_US_TTL_MS;
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);
        now += OfferFilterService.MAKER_IGNORES_US_TTL_MS;

        assertTrue(service.isIgnoredByMaker(offerOfMaker));
    }

    private static Offer offerFrom(String makerAddress) {
        Offer offer = mock(Offer.class);
        when(offer.getMakerNodeAddress()).thenReturn(new NodeAddress(makerAddress));
        return offer;
    }
}
