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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferFilterService;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.trade.bisq_v1.DumpDelayedPayoutTx;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bisq_v1.MakerProtocol;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.ClockWatcher;
import bisq.common.crypto.KeyRing;
import bisq.common.persistence.PersistenceManager;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The maker does not start a trade for a take offer request while the handshake of an earlier request for the same
 * offer is pending. The offer is available in all tests, as it is after the 60 seconds of its reservation.
 * <p>
 * A request which the maker handles reaches the validation of the request, which starts with the sender address. The
 * sender address of the request does not match here, so that no trade is started. A request which the maker ignores is
 * dropped before that.
 */
class TradeManagerTakeOfferHandshakeTest {
    private static final String OFFER_ID = "offerId";
    private static final long NOW = 1_800_000_000_000L;
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(MakerProtocol.TAKE_OFFER_HANDSHAKE_TIMEOUT_SEC);
    private static final NodeAddress TAKER = new NodeAddress("taker.onion", 9999);

    private OpenOfferManager openOfferManager;
    private TradeManager tradeManager;
    private InputsForDepositTxRequest request;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        KeyRing keyRing = mock(KeyRing.class);
        openOfferManager = mock(OpenOfferManager.class);
        tradeManager = new TradeManager(mock(User.class), keyRing, mock(BtcWalletService.class),
                mock(BsqWalletService.class), openOfferManager, mock(OfferFilterService.class),
                mock(ClosedTradableManager.class),
                mock(BsqSwapTradeManager.class), mock(FailedTradesManager.class), mock(P2PService.class),
                mock(PriceFeedService.class), mock(DelayedPayoutTxReceiverService.class),
                mock(TradeStatisticsManager.class), mock(TradeUtil.class), mock(MediatorManager.class),
                mock(Provider.class), mock(ClockWatcher.class), mock(PersistenceManager.class),
                mock(ReferralIdService.class), mock(CorePersistenceProtoResolver.class),
                mock(DumpDelayedPayoutTx.class), false) {
            @Override
            protected long now() {
                return NOW;
            }
        };

        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn(OFFER_ID);
        when(offer.isMyOffer(keyRing)).thenReturn(true);
        OpenOffer openOffer = new OpenOffer(offer);
        assertEquals(OpenOffer.State.AVAILABLE, openOffer.getState());
        when(openOfferManager.getOpenOfferById(OFFER_ID)).thenReturn(Optional.of(openOffer));

        request = mock(InputsForDepositTxRequest.class);
        when(request.getTradeId()).thenReturn(OFFER_ID);
        when(request.getSenderNodeAddress()).thenReturn(new NodeAddress("other.onion", 9999));
    }

    // The maker has answered the first request and waits for the taker, who may have published its taker fee tx.
    // After the 60 seconds of the reservation the offer is available again.
    @Test
    void requestIsIgnoredWhileTheHandshakeOfAnEarlierRequestIsPending() {
        addTrade(OFFER_ID, secondsAgo(61), false);

        receiveTakeOfferRequest();

        assertRequestIgnored();
    }

    @ParameterizedTest
    @CsvSource({
            "0, true",
            "60000, true",
            "119999, true",
            "120000, false",
            "121000, false"
    })
    void handshakeIsPendingUntilItsTimeout(long ageOfRequestInMs, boolean pending) {
        assertEquals(120_000, TIMEOUT_MS);
        addTrade(OFFER_ID, NOW - ageOfRequestInMs, false);

        assertEquals(pending, tradeManager.isTakeOfferHandshakePending(OFFER_ID));
        receiveTakeOfferRequest();

        if (pending) {
            assertRequestIgnored();
        } else {
            assertRequestHandled();
        }
    }

    // After the system clock was set back, the request of the earlier trade seems to be in the future. A clock set
    // back by less than the timeout keeps the handshake pending: the timer of the protocol on the desktop does not
    // follow the clock, and the offer is available again after its reservation. A request at least the timeout later
    // than now does not block the offer, as it would otherwise stay blocked for as long as the clock was set back:
    // after a restart, for example, no timeout of the protocol sets the error of the trade.
    @ParameterizedTest
    @CsvSource({
            "1, true",
            "60000, true",
            "119999, true",
            "120000, false",
            "3600000, false"
    })
    void handshakeIsPendingIfTheRequestIsLessThanTheTimeoutLaterThanNow(long requestLaterThanNowInMs,
                                                                         boolean pending) {
        addTrade(OFFER_ID, NOW + requestLaterThanNowInMs, false);

        assertEquals(pending, tradeManager.isTakeOfferHandshakePending(OFFER_ID));
        receiveTakeOfferRequest();

        if (pending) {
            assertRequestIgnored();
        } else {
            assertRequestHandled();
        }
    }

    // A failure of the maker's first step frees the offer at once, and another taker can take it
    @Test
    void requestIsHandledAfterTheEarlierTradeFailed() {
        addTrade(OFFER_ID, secondsAgo(10), true);

        assertFalse(tradeManager.isTakeOfferHandshakePending(OFFER_ID));
        receiveTakeOfferRequest();

        assertRequestHandled();
    }

    // A trade whose taker did not continue stays in the list, also over a restart, where no timeout sets an error
    @Test
    void requestIsHandledAfterTheTimeoutOfAnEarlierTradeWithoutError() {
        addTrade(OFFER_ID, secondsAgo(121), false);
        addTrade(OFFER_ID, NOW - TimeUnit.DAYS.toMillis(3), false);

        assertFalse(tradeManager.isTakeOfferHandshakePending(OFFER_ID));
        receiveTakeOfferRequest();

        assertRequestHandled();
    }

    @Test
    void requestIsHandledWithoutAnEarlierTrade() {
        assertFalse(tradeManager.isTakeOfferHandshakePending(OFFER_ID));
        receiveTakeOfferRequest();

        assertRequestHandled();
    }

    @Test
    void handshakeOfAnotherOfferDoesNotBlockTheOffer() {
        addTrade("otherOfferId", secondsAgo(61), false);

        assertTrue(tradeManager.isTakeOfferHandshakePending("otherOfferId"));
        assertFalse(tradeManager.isTakeOfferHandshakePending(OFFER_ID));
        receiveTakeOfferRequest();

        assertRequestHandled();
    }

    // Trades of earlier requests for the offer which failed or timed out stay in the list
    @Test
    void requestIsIgnoredIfOneOfSeveralTradesOfTheOfferIsInItsHandshake() {
        addTrade(OFFER_ID, secondsAgo(500), true);
        addTrade(OFFER_ID, secondsAgo(300), false);
        addTrade(OFFER_ID, secondsAgo(61), false);

        receiveTakeOfferRequest();

        assertRequestIgnored();
    }

    // The OpenOfferManager answers an availability request for the offer with "offer taken" in that time
    @SuppressWarnings("unchecked")
    @Test
    void openOfferManagerLearnsAboutThePendingHandshake() {
        ArgumentCaptor<Predicate<String>> takeOfferHandshakePending = ArgumentCaptor.forClass(Predicate.class);
        verify(openOfferManager).setTakeOfferHandshakePending(takeOfferHandshakePending.capture());
        addTrade(OFFER_ID, secondsAgo(61), false);
        addTrade("failedOfferId", secondsAgo(61), true);
        addTrade("oldOfferId", secondsAgo(121), false);

        assertTrue(takeOfferHandshakePending.getValue().test(OFFER_ID));
        assertFalse(takeOfferHandshakePending.getValue().test("failedOfferId"));
        assertFalse(takeOfferHandshakePending.getValue().test("oldOfferId"));
        assertFalse(takeOfferHandshakePending.getValue().test("unknownOfferId"));
    }

    private void receiveTakeOfferRequest() {
        DecryptedMessageWithPubKey message = mock(DecryptedMessageWithPubKey.class);
        when(message.getNetworkEnvelope()).thenReturn(request);
        tradeManager.onDirectMessage(message, TAKER);
    }

    private void assertRequestIgnored() {
        verify(request, never()).getSenderNodeAddress();
        verify(openOfferManager, never()).reserveOpenOffer(any());
    }

    private void assertRequestHandled() {
        verify(request).getSenderNodeAddress();
    }

    private void addTrade(String offerId, long takeOfferDate, boolean failed) {
        Trade trade = mock(Trade.class);
        when(trade.getId()).thenReturn(offerId);
        when(trade.getTakeOfferDate()).thenReturn(takeOfferDate);
        when(trade.hasFailed()).thenReturn(failed);
        when(trade.isInitialized()).thenReturn(true);
        tradeManager.addTradeToPendingTrades(trade);
    }

    private static long secondsAgo(long seconds) {
        return NOW - TimeUnit.SECONDS.toMillis(seconds);
    }
}
