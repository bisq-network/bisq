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
 * Pins down the phases in which the buyer accepts the
 * DepositTxAndDelayedPayoutTxMessage (see BuyerProtocol#handle). The condition is
 * built from the production phase list
 * (BuyerProtocol#DEPOSIT_TX_AND_DELAYED_PAYOUT_TX_MSG_PHASES), so a change there is
 * exercised by this test.
 *
 * DEPOSIT_CONFIRMED must be included: the deposit can confirm through the wallet
 * listener before the (mailbox) message is processed, so a message arriving after
 * the first confirmation must still be accepted. Otherwise the seller's payment
 * account payload is never stored and the trade is permanently stuck.
 */
public class BuyerDepositTxMessagePhaseTest {

    private enum TestEvent implements FluentProtocol.Event {
        MESSAGE_RECEIVED
    }

    private static FluentProtocol.Condition depositTxMessageCondition(Trade trade) {
        // phases mirror BuyerProtocol#handle; the phase list is the production
        // constant itself
        return new FluentProtocol.Condition(trade)
                .anyPhase(BuyerProtocol.DEPOSIT_TX_AND_DELAYED_PAYOUT_TX_MSG_PHASES)
                .with(TestEvent.MESSAGE_RECEIVED);
    }

    private static Trade tradeAt(Trade.Phase phase) {
        Trade trade = mock(Trade.class);
        when(trade.getTradePhase()).thenReturn(phase);
        when(trade.getId()).thenReturn("test-trade-id");
        return trade;
    }

    @Test
    public void messageAcceptedAfterDepositConfirmed() {
        // the fixed case: the deposit confirmed before the message was processed
        assertTrue(depositTxMessageCondition(
                tradeAt(Trade.Phase.DEPOSIT_CONFIRMED))
                .getResult().isValid());
    }

    @Test
    public void messageAcceptedWhileTakerFeeOrDepositPublished() {
        assertTrue(depositTxMessageCondition(
                tradeAt(Trade.Phase.TAKER_FEE_PUBLISHED))
                .getResult().isValid());
        assertTrue(depositTxMessageCondition(
                tradeAt(Trade.Phase.DEPOSIT_PUBLISHED))
                .getResult().isValid());
    }

    @Test
    public void messageRejectedInLaterPhases() {
        assertFalse(depositTxMessageCondition(
                tradeAt(Trade.Phase.FIAT_SENT))
                .getResult().isValid());
        assertFalse(depositTxMessageCondition(
                tradeAt(Trade.Phase.PAYOUT_PUBLISHED))
                .getResult().isValid());
    }

    @Test
    public void messageRejectedBeforeTakerFeePublished() {
        assertFalse(depositTxMessageCondition(
                tradeAt(Trade.Phase.INIT))
                .getResult().isValid());
    }
}
