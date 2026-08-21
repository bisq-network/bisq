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

package bisq.core.trade.protocol.bsq_swap;


import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapRequest;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;

public interface BsqSwapMakerProtocol {
    void handleTakeOfferRequest(BsqSwapRequest request,
                                NodeAddress sender,
                                ErrorMessageHandler errorMessageHandler);
}
