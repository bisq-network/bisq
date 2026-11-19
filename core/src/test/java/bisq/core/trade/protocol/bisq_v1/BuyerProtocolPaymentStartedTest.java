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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.FluentProtocol;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSendCounterCurrencyTransferStartedMessage;

import bisq.common.taskrunner.Task;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The first confirmation of the payment runs only in DEPOSIT_CONFIRMED, a retry only in FIAT_SENT. Neither moves the
 * trade back to an earlier phase.
 */
class BuyerProtocolPaymentStartedTest {
    @Test
    void firstConfirmationSetsTheConfirmedStateAndSendsTheMessage() {
        BuyerAsTakerTrade trade = trade(Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN);
        TestBuyerProtocol protocol = new TestBuyerProtocol(trade);

        protocol.onPaymentStarted(() -> {
        }, message -> {
        });

        verify(trade).setState(Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED);
        assertTrue(protocol.executedTasks.contains(BuyerSendCounterCurrencyTransferStartedMessage.class));
    }

    // Without the reset a second failure sets BUYER_SEND_FAILED again, which observers of the state do not see
    @Test
    void retryAfterAFailedSendRestartsTheStatesOfThePhase() {
        BuyerAsTakerTrade trade = trade(Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG);
        TestBuyerProtocol protocol = new TestBuyerProtocol(trade);

        protocol.retryPaymentStarted(() -> {
        }, message -> {
        });

        verify(trade).setState(Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED);
        assertTrue(protocol.executedTasks.contains(BuyerSendCounterCurrencyTransferStartedMessage.class));
    }

    @ParameterizedTest
    @EnumSource(value = Trade.State.class, names = {
            "BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED",
            "BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG",
            "BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG",
            "BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG"})
    void retryKeepsTheStateOfAPendingOrDeliveredMessage(Trade.State state) {
        BuyerAsTakerTrade trade = trade(state);
        TestBuyerProtocol protocol = new TestBuyerProtocol(trade);

        protocol.retryPaymentStarted(() -> {
        }, message -> {
        });

        verify(trade, never()).setState(any());
        assertTrue(protocol.executedTasks.contains(BuyerSendCounterCurrencyTransferStartedMessage.class));
    }

    @ParameterizedTest
    @EnumSource(value = Trade.State.class, names = {
            "DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN",
            "BUYER_SAW_PAYOUT_TX_IN_NETWORK",
            "BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG"})
    void retryOutsideFiatSentDoesNothing(Trade.State state) {
        BuyerAsTakerTrade trade = trade(state);
        TestBuyerProtocol protocol = new TestBuyerProtocol(trade);

        protocol.retryPaymentStarted(() -> {
        }, message -> {
        });

        verify(trade, never()).setState(any());
        assertTrue(protocol.executedTasks.isEmpty());
    }

    @Test
    void firstConfirmationInFiatSentDoesNothing() {
        BuyerAsTakerTrade trade = trade(Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG);
        TestBuyerProtocol protocol = new TestBuyerProtocol(trade);

        protocol.onPaymentStarted(() -> {
        }, message -> {
        });

        verify(trade, never()).setState(any());
        assertTrue(protocol.executedTasks.isEmpty());
    }

    private static BuyerAsTakerTrade trade(Trade.State state) {
        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        when(processModel.getTradePeer()).thenReturn(mock(TradingPeer.class));

        BuyerAsTakerTrade trade = mock(BuyerAsTakerTrade.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getTradeProtocolModel()).thenReturn(processModel);
        when(trade.getOffer()).thenReturn(mock(Offer.class));
        when(trade.getId()).thenReturn("trade-id");
        when(trade.getTradeState()).thenReturn(state);
        when(trade.getTradePhase()).thenReturn(state.getTradePhase());
        when(trade.confirmPermitted()).thenReturn(true);
        return trade;
    }

    // Records the tasks the protocol would run, without running them
    private static final class TestBuyerProtocol extends BuyerAsTakerProtocol {
        private final List<Class<? extends Task<TradeModel>>> executedTasks = new ArrayList<>();

        private TestBuyerProtocol(BuyerAsTakerTrade trade) {
            super(trade);
        }

        @Override
        protected FluentProtocol expect(FluentProtocol.Condition condition) {
            return new FluentProtocol(this) {
                private Class<? extends Task<TradeModel>>[] tasks;

                {
                    condition(condition);
                }

                @Override
                public FluentProtocol setup(Setup setup) {
                    tasks = setup.getTasks();
                    return this;
                }

                @Override
                public FluentProtocol executeTasks() {
                    return run(() -> executedTasks.addAll(List.of(tasks)));
                }
            };
        }
    }
}
