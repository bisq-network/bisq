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
import bisq.core.offer.OfferFilterService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.bisq_v1.DumpDelayedPayoutTx;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.ClockWatcher;
import bisq.common.UserThread;
import bisq.common.crypto.KeyRing;
import bisq.common.persistence.PersistenceManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeManagerFailedTradeProtocolTest {
    private final P2PService p2PService = mock(P2PService.class);
    private SellerAsMakerTrade trade;
    private TradeManager tradeManager;

    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));
        trade = trade("trade-uid");

        tradeManager = new TradeManager(mock(User.class), mock(KeyRing.class), mock(BtcWalletService.class),
                mock(BsqWalletService.class), mock(OpenOfferManager.class), mock(OfferFilterService.class),
                mock(ClosedTradableManager.class),
                mock(BsqSwapTradeManager.class), mock(FailedTradesManager.class), p2PService,
                mock(PriceFeedService.class), mock(DelayedPayoutTxReceiverService.class),
                mock(TradeStatisticsManager.class), mock(TradeUtil.class), mock(MediatorManager.class),
                mock(Provider.class), mock(ClockWatcher.class), mock(PersistenceManager.class),
                mock(ReferralIdService.class), mock(CorePersistenceProtoResolver.class),
                mock(DumpDelayedPayoutTx.class), false);
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
    }

    @Test
    void theProtocolOfATradeMovedToTheFailedTradesIsShutDown() {
        TradeProtocol protocol = tradeManager.getTradeProtocol(trade);

        tradeManager.onMoveInvalidTradeToFailedTrades(trade);

        assertTrue(protocol.isShutDown());
        verify(p2PService).removeDecryptedDirectMessageListener(protocol);
    }

    // A service such as the automatic confirmation of XMR payments can still ask for the protocol of the trade
    @Test
    void aFailedTradeKeepsItsShutDownProtocol() {
        TradeProtocol protocol = tradeManager.getTradeProtocol(trade);

        tradeManager.onMoveInvalidTradeToFailedTrades(trade);

        assertSame(protocol, tradeManager.getTradeProtocol(trade));
    }

    // The trade was moved to the failed trades in this session, so it is initialized already
    @Test
    void aFailedTradeThatReturnsToThePendingTradesGetsANewProtocol() {
        TradeProtocol shutDownProtocol = tradeManager.getTradeProtocol(trade);
        tradeManager.onMoveInvalidTradeToFailedTrades(trade);
        tradeManager.getTradeProtocol(trade);

        tradeManager.addTradeToPendingTrades(trade);

        TradeProtocol protocol = tradeManager.getTradeProtocol(trade);
        assertNotSame(shutDownProtocol, protocol);
        assertFalse(protocol.isShutDown());
        // Initialized: it listens for the messages of the trade
        verify(p2PService).addDecryptedDirectMessageListener(protocol);
        verify(trade, never()).setErrorMessage(any());
    }

    // A dispute that the peer opens reverts a closed trade to the pending trades. Its protocol runs already.
    @Test
    void aTradeWithARunningProtocolThatReturnsToThePendingTradesKeepsItsProtocol() {
        TradeProtocol protocol = tradeManager.getTradeProtocol(trade);

        tradeManager.addTradeToPendingTrades(trade);

        assertSame(protocol, tradeManager.getTradeProtocol(trade));
        // Not initialized a second time
        verify(p2PService, never()).addDecryptedDirectMessageListener(protocol);
    }

    // The offer of a failed trade can be taken again, so a newer pending trade can have the same offer id
    @Test
    void aReturningTradeDoesNotResetTheProtocolOfANewerTradeOfTheSameOffer() {
        tradeManager.getTradeProtocol(trade);
        tradeManager.onMoveInvalidTradeToFailedTrades(trade);
        SellerAsMakerTrade newerTrade = trade("newer-trade-uid");
        TradeProtocol newerProtocol = tradeManager.getTradeProtocol(newerTrade);

        tradeManager.addTradeToPendingTrades(trade);

        assertFalse(newerProtocol.isShutDown());
        verify(newerTrade, never()).setErrorMessage(any());
        verify(p2PService, never()).removeDecryptedDirectMessageListener(newerProtocol);
    }

    // As for a trade that never failed, a newer trade of the same offer resets the protocol of the returned trade
    @Test
    void aNewerTradeOfTheSameOfferResetsTheNewProtocolOfAReturnedTrade() {
        tradeManager.getTradeProtocol(trade);
        tradeManager.onMoveInvalidTradeToFailedTrades(trade);
        tradeManager.addTradeToPendingTrades(trade);
        TradeProtocol protocol = tradeManager.getTradeProtocol(trade);

        tradeManager.getTradeProtocol(trade("newer-trade-uid"));

        verify(p2PService).removeDecryptedDirectMessageListener(protocol);
    }

    private SellerAsMakerTrade trade(String uid) {
        SellerAsMakerTrade trade = mock(SellerAsMakerTrade.class);
        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.getP2PService()).thenReturn(p2PService);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getTradeProtocolModel()).thenReturn(processModel);
        when(trade.getId()).thenReturn("trade-id");
        when(trade.getUid()).thenReturn(uid);
        when(trade.isInitialized()).thenReturn(true);
        return trade;
    }
}
