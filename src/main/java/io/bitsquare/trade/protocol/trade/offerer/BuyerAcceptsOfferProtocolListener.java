/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.offerer;

import io.bitsquare.trade.Offer;

import com.google.bitcoin.core.TransactionConfidence;

public interface BuyerAcceptsOfferProtocolListener {
    void onOfferAccepted(Offer offer);

    void onDepositTxPublished(String depositTxID);

    void onDepositTxConfirmedInBlockchain();

    void onDepositTxConfirmedUpdate(TransactionConfidence confidence);

    void onPayoutTxPublished(String payoutTxID);

    void onFault(Throwable throwable, BuyerAcceptsOfferProtocol.State state);

    void onWaitingForPeerResponse(BuyerAcceptsOfferProtocol.State state);

    void onCompleted(BuyerAcceptsOfferProtocol.State state);

    void onWaitingForUserInteraction(BuyerAcceptsOfferProtocol.State state);
}
