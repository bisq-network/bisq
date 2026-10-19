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

import bisq.common.app.Version;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OfferFilterServiceMakerIgnoresUsTest {
    private long now;
    private Preferences preferences;
    private FilterPolicyService filterPolicyService;
    private OfferFilterService service;
    private final Offer offerOfMaker = offerFrom("maker.onion:9999");
    private final Offer otherOfferOfMaker = offerFrom("maker.onion:9999");
    private final Offer offerOfOther = offerFrom("other.onion:9999");

    @BeforeEach
    public void setUp() {
        now = 1_000_000L;
        preferences = mock(Preferences.class);
        filterPolicyService = mock(FilterPolicyService.class);
        service = new OfferFilterService(null, preferences, filterPolicyService, mock(AccountAgeWitnessService.class)) {
            @Override
            protected long now() {
                return now;
            }

            @Override
            public boolean isAnyPaymentAccountValidForOffer(Offer offer) {
                return true;
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

    @Test
    public void canTakeOfferNamesTheIgnoreOfTheMaker() {
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);

        assertEquals(OfferFilterService.Result.IS_IGNORED_BY_MAKER, service.canTakeOffer(offerOfMaker, false));
    }

    @Test
    public void ourOwnIgnoreOfTheMakerComesFirst() {
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);
        when(preferences.getIgnoreTradersList()).thenReturn(List.of("maker.onion:9999"));

        assertEquals(OfferFilterService.Result.IS_IGNORED, service.canTakeOffer(offerOfMaker, false));
    }

    // The reasons from the filter apply to every user, so they are shown instead of the maker's ignore
    @ParameterizedTest
    @EnumSource(value = OfferFilterService.Result.class, names = {"IS_OFFER_BANNED", "IS_CURRENCY_BANNED",
            "IS_PAYMENT_METHOD_BANNED", "IS_NODE_ADDRESS_BANNED", "REQUIRE_UPDATE_TO_NEW_VERSION"})
    public void aBanOrARequiredUpdateComesBeforeTheIgnoreOfTheMaker(OfferFilterService.Result reason) {
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);
        switch (reason) {
            case IS_OFFER_BANNED -> when(filterPolicyService.isOfferIdBanned(any())).thenReturn(true);
            case IS_CURRENCY_BANNED -> when(filterPolicyService.isCurrencyBanned(any())).thenReturn(true);
            case IS_PAYMENT_METHOD_BANNED -> when(filterPolicyService.isPaymentMethodBanned(any())).thenReturn(true);
            case IS_NODE_ADDRESS_BANNED -> when(filterPolicyService.isNodeAddressBanned(any())).thenReturn(true);
            default -> when(filterPolicyService.requireUpdateToNewVersionForTrading()).thenReturn(true);
        }

        assertEquals(reason, service.canTakeOffer(offerOfMaker, false));
    }

    // No other payment account helps while the maker ignores us, so the trade limit is not the reason shown
    @Test
    public void theIgnoreOfTheMakerComesBeforeTheCounterpartyTradeLimit() {
        service.onAvailabilityAnswer(offerOfMaker, AvailabilityResult.USER_IGNORED);
        // The counterparty trade limit is insufficient: a fiat offer whose amount the mock does not verify
        when(offerOfMaker.isFiatOffer()).thenReturn(true);

        assertEquals(OfferFilterService.Result.IS_IGNORED_BY_MAKER, service.canTakeOffer(offerOfMaker, false));
    }

    private static Offer offerFrom(String makerAddress) {
        Offer offer = mock(Offer.class);
        when(offer.getMakerNodeAddress()).thenReturn(new NodeAddress(makerAddress));
        when(offer.getProtocolVersion()).thenReturn((long) Version.TRADE_PROTOCOL_VERSION);
        return offer;
    }
}
