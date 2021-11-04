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

package bisq.core.trade.protocol.bsq_swap.messages;

import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public abstract class BsqSwapRequest extends TradeMessage implements DirectMessage {
    protected final NodeAddress senderNodeAddress;
    protected final PubKeyRing takerPubKeyRing;
    protected final long tradeAmount;
    protected final long txFeePerVbyte;
    protected final long makerFee;
    protected final long takerFee;
    protected final long tradeDate;

    protected BsqSwapRequest(int messageVersion,
                             String tradeId,
                             String uid,
                             NodeAddress senderNodeAddress,
                             PubKeyRing takerPubKeyRing,
                             long tradeAmount,
                             long txFeePerVbyte,
                             long makerFee,
                             long takerFee,
                             long tradeDate) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.takerPubKeyRing = takerPubKeyRing;
        this.tradeAmount = tradeAmount;
        this.txFeePerVbyte = txFeePerVbyte;
        this.makerFee = makerFee;
        this.takerFee = takerFee;
        this.tradeDate = tradeDate;
    }
}
