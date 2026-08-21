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

package bisq.core.trade.model.bisq_v1;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.model.bisq_v1.Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED;
import static bisq.core.trade.model.bisq_v1.Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG;
import static bisq.core.trade.model.bisq_v1.Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG;
import static bisq.core.trade.model.bisq_v1.Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG;
import static bisq.core.trade.model.bisq_v1.Trade.State.BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG;
import static bisq.core.trade.model.bisq_v1.Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * State transitions the startup re-send of the CounterCurrencyTransferStartedMessage
 * relies on (see BuyerProtocol#onInitialized and
 * BuyerSendCounterCurrencyTransferStartedMessage): all FIAT_SENT sub-states must be
 * able to move to BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG once the seller's ACK
 * arrives, regardless of which delivery attempt got stuck before.
 */
public class TradeStateTransitionTest {

    @Test
    public void ackAfterAnyStuckDeliveryStateIsAValidTransition() {
        // sent but never ACKed (e.g. app restarted before the ACK arrived)
        assertTrue(BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG
                .isValidTransitionTo(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG));

        // stored in the peer's mailbox, ACK arrives after they came back online
        assertTrue(BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG
                .isValidTransitionTo(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG));

        // send failed, a later re-send attempt got ACKed
        assertTrue(BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG
                .isValidTransitionTo(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG));
    }

    @Test
    public void deliveryStatesCanMoveBetweenEachOther() {
        // same-phase moves between the delivery sub-states are permitted, so a
        // re-send attempt may downgrade/upgrade the delivery marker freely
        assertTrue(BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG
                .isValidTransitionTo(BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG));
        assertTrue(BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG
                .isValidTransitionTo(BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG));
        assertTrue(BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED
                .isValidTransitionTo(BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG));
    }

    @Test
    public void movingBackToAnEarlierPhaseIsInvalid() {
        assertFalse(BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG
                .isValidTransitionTo(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN));
        assertFalse(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG
                .isValidTransitionTo(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN));
    }
}
