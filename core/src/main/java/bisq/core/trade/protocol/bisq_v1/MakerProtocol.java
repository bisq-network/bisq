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


import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;

public interface MakerProtocol {
    // After a take offer request the maker waits that long for the next message of the taker, with which the maker
    // removes the offer. Without that message the trade gets the timeout error.
    int TAKE_OFFER_HANDSHAKE_TIMEOUT_SEC = 120;

    void handleTakeOfferRequest(InputsForDepositTxRequest message,
                                NodeAddress taker,
                                ErrorMessageHandler errorMessageHandler);
}
