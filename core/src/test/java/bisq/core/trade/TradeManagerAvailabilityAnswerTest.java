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

package bisq.core.trade;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferFilterService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.availability.AvailabilityResult;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.trade.bisq_v1.DumpDelayedPayoutTx;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.ClockWatcher;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TradeManager passes the maker's answer to an availability request to OfferFilterService. Only the network request of
 * the offer is replaced: it sets the answer on the offer and calls the result handler for AVAILABLE and the error
 * handler for every other answer, as ProcessOfferAvailabilityResponse does.
 */
class TradeManagerAvailabilityAnswerTest {
    private OfferFilterService offerFilterService;
    private TradeManager tradeManager;
    private final Offer offer = offerFrom("maker.onion:9999");
    private final Offer otherOfferOfMaker = offerFrom("maker.onion:9999");

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        offerFilterService = new OfferFilterService(null, mock(Preferences.class), mock(FilterPolicyService.class),
                mock(AccountAgeWitnessService.class));
        tradeManager = new TradeManager(mock(User.class), mock(KeyRing.class), mock(BtcWalletService.class),
                mock(BsqWalletService.class), mock(OpenOfferManager.class), offerFilterService,
                mock(ClosedTradableManager.class),
                mock(BsqSwapTradeManager.class), mock(FailedTradesManager.class), mock(P2PService.class),
                mock(PriceFeedService.class), mock(DelayedPayoutTxReceiverService.class),
                mock(TradeStatisticsManager.class), mock(TradeUtil.class), mock(MediatorManager.class),
                mock(Provider.class), mock(ClockWatcher.class), mock(PersistenceManager.class),
                mock(ReferralIdService.class), mock(CorePersistenceProtoResolver.class),
                mock(DumpDelayedPayoutTx.class), false);
    }

    // The availability protocol reports every answer other than AVAILABLE through the error handler
    @Test
    void refusalClosesAllOffersOfTheMaker() {
        answer(offer, AvailabilityResult.USER_IGNORED, false);

        assertTrue(offerFilterService.isIgnoredByMaker(offer));
        assertTrue(offerFilterService.isIgnoredByMaker(otherOfferOfMaker));
    }

    @Test
    void acceptanceReopensTheMaker() {
        answer(offer, AvailabilityResult.USER_IGNORED, false);
        assertTrue(offerFilterService.isIgnoredByMaker(otherOfferOfMaker));

        answer(otherOfferOfMaker, AvailabilityResult.AVAILABLE, true);

        assertFalse(offerFilterService.isIgnoredByMaker(offer));
    }

    // Positive control: another answer leaves the maker open
    @Test
    void otherAnswerLeavesTheMakerOpen() {
        answer(offer, AvailabilityResult.PRICE_OUT_OF_TOLERANCE, false);

        assertFalse(offerFilterService.isIgnoredByMaker(otherOfferOfMaker));
    }

    private void answer(Offer offer, AvailabilityResult result, boolean completes) {
        when(offer.getAvailabilityResult()).thenReturn(result);
        doAnswer(invocation -> {
            if (completes) {
                ((ResultHandler) invocation.getArgument(1)).handleResult();
            } else {
                ((ErrorMessageHandler) invocation.getArgument(2)).handleErrorMessage("request failed");
            }
            return null;
        }).when(offer).checkOfferAvailability(any(), any(), any());

        tradeManager.checkOfferAvailability(offer, false, () -> {
        }, errorMessage -> {
        });
    }

    private static Offer offerFrom(String makerAddress) {
        Offer offer = mock(Offer.class);
        when(offer.getMakerNodeAddress()).thenReturn(new NodeAddress(makerAddress));
        return offer;
    }
}
