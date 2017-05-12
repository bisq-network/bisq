/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class FinalizePayoutTxRequest extends TradeMessage implements MailboxMessage {
    private final byte[] sellerSignature;
    private final String sellerPayoutAddress;
    private final NodeAddress senderNodeAddress;
    private final String uid;

    public FinalizePayoutTxRequest(String tradeId,
                                   byte[] sellerSignature,
                                   String sellerPayoutAddress,
                                   NodeAddress senderNodeAddress,
                                   String uid) {
        super(tradeId);
        this.sellerSignature = sellerSignature;
        this.sellerPayoutAddress = sellerPayoutAddress;
        this.senderNodeAddress = senderNodeAddress;
        this.uid = uid;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder msgBuilder = NetworkEnvelope.getDefaultBuilder();
        return msgBuilder.setFinalizePayoutTxRequest(PB.FinalizePayoutTxRequest.newBuilder()
                .setMessageVersion(getMessageVersion())
                .setTradeId(getTradeId())
                .setSellerSignature(ByteString.copyFrom(sellerSignature))
                .setSellerPayoutAddress(sellerPayoutAddress)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setUid(uid)).build();
    }
}
