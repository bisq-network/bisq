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

public enum CanceledTradeState {
    REQUEST_MSG_SENT,
    REQUEST_MSG_ARRIVED,
    REQUEST_MSG_IN_MAILBOX,
    REQUEST_MSG_SEND_FAILED,

    // Peer received request
    RECEIVED_CANCEL_REQUEST,

    // Requester received reject msg
    RECEIVED_ACCEPTED_MSG,

    // Peer accepted
    PAYOUT_TX_PUBLISHED,
    PAYOUT_TX_PUBLISHED_MSG_SENT,
    PAYOUT_TX_PUBLISHED_MSG_ARRIVED,
    PAYOUT_TX_PUBLISHED_MSG_IN_MAILBOX,
    PAYOUT_TX_PUBLISHED_MSG_SEND_FAILED,

    // Request sees tx
    PAYOUT_TX_SEEN_IN_NETWORK,

    // Peer rejected
    REQUEST_CANCELED_MSG_SENT,
    REQUEST_CANCELED_MSG_ARRIVED,
    REQUEST_CANCELED_MSG_IN_MAILBOX,
    REQUEST_CANCELED_MSG_SEND_FAILED,

    // Requester received reject msg
    RECEIVED_REJECTED_MSG,
}
