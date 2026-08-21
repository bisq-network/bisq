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

package bisq.core.trade.protocol;

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.BuyerProtocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins down which trade states re-run BuyerSendCounterCurrencyTransferStartedMessage
 * at STARTUP (see BuyerProtocol#onInitialized). The condition is built from the
 * production state list (BuyerProtocol#STARTUP_RESEND_PAYMENT_STARTED_STATES), so a
 * change there is exercised by this test.
 *
 * BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG must be included: it is the state of a
 * buyer who sent the message but has not received the seller's ACK yet. The
 * in-memory re-send timer does not survive a restart, so without matching this
 * state at startup such a trade would never re-send and the seller might never
 * learn the payment was started.
 */
public class BuyerStartupResendConditionTest {

    private enum TestEvent implements FluentProtocol.Event {
        STARTUP
    }

    private static FluentProtocol.Condition startupResendCondition(Trade trade) {
        // phases and event mirror BuyerProtocol#onInitialized; the state list is
        // the production constant itself
        return new FluentProtocol.Condition(trade)
                .anyPhase(Trade.Phase.FIAT_SENT, Trade.Phase.FIAT_RECEIVED)
                .anyState(BuyerProtocol.STARTUP_RESEND_PAYMENT_STARTED_STATES)
                .with(TestEvent.STARTUP);
    }

    private static Trade tradeAt(Trade.State state) {
        Trade trade = mock(Trade.class);
        when(trade.getTradeState()).thenReturn(state);
        when(trade.getTradePhase()).thenReturn(state.getTradePhase());
        when(trade.getId()).thenReturn("test-trade-id");
        return trade;
    }

    @Test
    public void resendFiresWhenMessageWasSentButNotAcked() {
        // the fixed case: restart happened after sending, before the seller's ACK
        assertTrue(startupResendCondition(
                tradeAt(Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG))
                .getResult().isValid());
    }

    @Test
    public void resendFiresForMailboxAndFailedStates() {
        assertTrue(startupResendCondition(
                tradeAt(Trade.State.BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG))
                .getResult().isValid());
        assertTrue(startupResendCondition(
                tradeAt(Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG))
                .getResult().isValid());
    }

    @Test
    public void resendDoesNotFireOnceAckArrived() {
        assertFalse(startupResendCondition(
                tradeAt(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG))
                .getResult().isValid());
    }

    @Test
    public void resendDoesNotFireBeforePaymentStarted() {
        assertFalse(startupResendCondition(
                tradeAt(Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN))
                .getResult().isValid());
        assertFalse(startupResendCondition(
                tradeAt(Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED))
                .getResult().isValid());
    }
}
