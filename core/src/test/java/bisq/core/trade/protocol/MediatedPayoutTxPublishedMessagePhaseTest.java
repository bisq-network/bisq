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
import bisq.core.trade.protocol.bisq_v1.DisputeProtocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins down the phases in which a trader accepts the MediatedPayoutTxPublishedMessage
 * (see DisputeProtocol#handle). The condition is built from the production phase list
 * (DisputeProtocol#MEDIATED_PAYOUT_TX_PUBLISHED_MSG_PHASES), so a change there is
 * exercised by this test.
 *
 * PAYOUT_PUBLISHED must be included: both traders can publish the mediated payout, so
 * the peer's message can arrive after our own payout was published.
 */
public class MediatedPayoutTxPublishedMessagePhaseTest {
    private enum TestEvent implements FluentProtocol.Event {
        MESSAGE_RECEIVED
    }

    private static FluentProtocol.Condition mediatedPayoutTxPublishedMessageCondition(Trade trade) {
        return new FluentProtocol.Condition(trade)
                .anyPhase(DisputeProtocol.MEDIATED_PAYOUT_TX_PUBLISHED_MSG_PHASES)
                .with(TestEvent.MESSAGE_RECEIVED);
    }

    private static Trade tradeAt(Trade.Phase phase) {
        Trade trade = mock(Trade.class);
        when(trade.getTradePhase()).thenReturn(phase);
        when(trade.getId()).thenReturn("test-trade-id");
        return trade;
    }

    @Test
    public void messageAcceptedAfterOwnPayoutWasPublished() {
        // the fixed case: we published the mediated payout ourselves before the peer's message arrived
        assertTrue(mediatedPayoutTxPublishedMessageCondition(
                tradeAt(Trade.Phase.PAYOUT_PUBLISHED))
                .getResult().isValid());
    }

    @Test
    public void messageAcceptedWhileWaitingForThePayout() {
        assertTrue(mediatedPayoutTxPublishedMessageCondition(
                tradeAt(Trade.Phase.DEPOSIT_CONFIRMED))
                .getResult().isValid());
        assertTrue(mediatedPayoutTxPublishedMessageCondition(
                tradeAt(Trade.Phase.FIAT_SENT))
                .getResult().isValid());
        assertTrue(mediatedPayoutTxPublishedMessageCondition(
                tradeAt(Trade.Phase.FIAT_RECEIVED))
                .getResult().isValid());
    }

    @Test
    public void messageRejectedBeforeDepositConfirmedAndAfterWithdrawal() {
        assertFalse(mediatedPayoutTxPublishedMessageCondition(
                tradeAt(Trade.Phase.DEPOSIT_PUBLISHED))
                .getResult().isValid());
        assertFalse(mediatedPayoutTxPublishedMessageCondition(
                tradeAt(Trade.Phase.WITHDRAWN))
                .getResult().isValid());
    }
}
