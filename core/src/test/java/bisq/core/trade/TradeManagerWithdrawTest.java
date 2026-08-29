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

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
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
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import bisq.common.ClockWatcher;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeManagerWithdrawTest {
    private static final String TRADE_ID = "tradeId";
    private static final Coin AMOUNT = Coin.valueOf(2_000);
    private static final Coin FEE = Coin.valueOf(1_000);

    private TradeManager tradeManager;
    private BtcWalletService btcWalletService;
    private Trade trade;
    private TradeProtocol tradeProtocol;
    private ResultHandler resultHandler;
    private FaultHandler faultHandler;
    private FaultHandler broadcastFailureHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        btcWalletService = mock(BtcWalletService.class);
        AddressEntry addressEntry = mock(AddressEntry.class);
        when(addressEntry.getAddressString()).thenReturn("fromAddress");
        when(btcWalletService.getOrCreateAddressEntry(TRADE_ID, AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(addressEntry);

        tradeManager = spy(new TradeManager(mock(User.class), mock(KeyRing.class), btcWalletService,
                mock(BsqWalletService.class), mock(OpenOfferManager.class), mock(ClosedTradableManager.class),
                mock(BsqSwapTradeManager.class), mock(FailedTradesManager.class), mock(P2PService.class),
                mock(PriceFeedService.class), mock(DelayedPayoutTxReceiverService.class),
                mock(TradeStatisticsManager.class), mock(TradeUtil.class), mock(MediatorManager.class),
                mock(Provider.class), mock(ClockWatcher.class), mock(PersistenceManager.class),
                mock(ReferralIdService.class), mock(CorePersistenceProtoResolver.class),
                mock(DumpDelayedPayoutTx.class), false));

        trade = mock(Trade.class);
        when(trade.getId()).thenReturn(TRADE_ID);
        tradeProtocol = mock(TradeProtocol.class);
        doReturn(tradeProtocol).when(tradeManager).getTradeProtocol(trade);

        resultHandler = mock(ResultHandler.class);
        faultHandler = mock(FaultHandler.class);
        broadcastFailureHandler = mock(FaultHandler.class);
    }

    @Test
    void committedSendCompletesTheTradeExactlyOnce() throws Exception {
        withdraw();

        verify(resultHandler, times(1)).handleResult();
        verify(trade).setState(Trade.State.WITHDRAW_COMPLETED);
        verify(tradeManager).onTradeCompleted(trade);
        verify(tradeProtocol).onWithdrawCompleted();
        verify(tradeManager, atLeastOnce()).requestPersistence();
        verifyNoInteractions(faultHandler);
        verifyNoInteractions(broadcastFailureHandler);
    }

    @Test
    void laterBroadcastFailureIsANotificationNotASecondOutcome() throws Exception {
        FutureCallback<Transaction> callback = withdraw();

        callback.onFailure(new RuntimeException("rejected by peers"));

        verify(resultHandler, times(1)).handleResult();
        verify(broadcastFailureHandler, times(1)).handleFault(anyString(), any());
        verifyNoInteractions(faultHandler);
    }

    @Test
    void preCommitFailureFaultsWithoutCompletingTheTrade() throws Exception {
        doThrow(new InsufficientMoneyException(Coin.valueOf(1)))
                .when(btcWalletService).sendFunds(any(), any(), any(), any(), any(), any(), any(), any());

        tradeManager.onWithdrawRequest("toAddress", AMOUNT, FEE, null, trade,
                null, resultHandler, faultHandler, broadcastFailureHandler);

        verify(faultHandler, times(1)).handleFault(anyString(), any());
        verify(trade, never()).setState(any());
        verify(tradeManager, never()).onTradeCompleted(any());
        verifyNoInteractions(resultHandler);
        verifyNoInteractions(broadcastFailureHandler);
    }

    @SuppressWarnings("unchecked")
    private FutureCallback<Transaction> withdraw() throws Exception {
        ArgumentCaptor<FutureCallback<Transaction>> captor = ArgumentCaptor.forClass(FutureCallback.class);
        tradeManager.onWithdrawRequest("toAddress", AMOUNT, FEE, null, trade,
                null, resultHandler, faultHandler, broadcastFailureHandler);
        verify(btcWalletService).sendFunds(eq("fromAddress"), eq("toAddress"), any(), any(), isNull(),
                eq(AddressEntry.Context.TRADE_PAYOUT), isNull(), captor.capture());
        return captor.getValue();
    }
}
