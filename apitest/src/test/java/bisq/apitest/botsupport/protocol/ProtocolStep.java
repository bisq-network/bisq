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

package bisq.apitest.botsupport.protocol;

public enum ProtocolStep {
    START,
    FIND_OFFER,
    TAKE_OFFER,
    WAIT_FOR_OFFER_TAKER,
    WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED,
    WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED,
    SEND_PAYMENT_TO_RCV_ADDRESS,
    SEND_PAYMENT_STARTED_MESSAGE,
    WAIT_FOR_PAYMENT_STARTED_MESSAGE,
    WAIT_FOR_BSQ_PAYMENT_TO_RCV_ADDRESS,
    SEND_PAYMENT_RECEIVED_CONFIRMATION_MESSAGE,
    WAIT_FOR_PAYMENT_RECEIVED_CONFIRMATION_MESSAGE,
    WAIT_FOR_PAYOUT_TX,
    KEEP_FUNDS,
    DONE
}
