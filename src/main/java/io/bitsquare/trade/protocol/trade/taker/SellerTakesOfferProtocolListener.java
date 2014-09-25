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

package io.bitsquare.trade.protocol.trade.taker;

import io.bitsquare.trade.Trade;

import com.google.bitcoin.core.Transaction;

public interface SellerTakesOfferProtocolListener {
    void onDepositTxPublished(Transaction depositTx);

    void onBankTransferInited(String tradeId);

    void onPayoutTxPublished(Trade trade, Transaction payoutTx);

    void onFault(Throwable throwable, SellerTakesOfferProtocol.State state);

    void onWaitingForPeerResponse(SellerTakesOfferProtocol.State state);

    void onTakeOfferRequestAccepted(Trade trade);

    void onTakeOfferRequestRejected(Trade trade);

}
