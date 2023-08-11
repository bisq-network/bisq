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

package bisq.core.trade.protocol.bisq_v5.messages;

import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class BuyersRedirectSellerSignatureResponse extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] buyersRedirectTxSellerSignature;

    public BuyersRedirectSellerSignatureResponse(String uid,
                                                 String tradeId,
                                                 NodeAddress senderNodeAddress,
                                                 byte[] buyersRedirectTxSellerSignature) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                buyersRedirectTxSellerSignature);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BuyersRedirectSellerSignatureResponse(int messageVersion,
                                                  String uid,
                                                  String tradeId,
                                                  NodeAddress senderNodeAddress,
                                                  byte[] buyersRedirectTxSellerSignature) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.buyersRedirectTxSellerSignature = buyersRedirectTxSellerSignature;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setBuyersRedirectSellerSignatureResponse(protobuf.BuyersRedirectSellerSignatureResponse.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setBuyersRedirectTxSellerSignature(ByteString.copyFrom(buyersRedirectTxSellerSignature))
                )
                .build();
    }

    public static BuyersRedirectSellerSignatureResponse fromProto(protobuf.BuyersRedirectSellerSignatureResponse proto,
                                                                  int messageVersion) {
        return new BuyersRedirectSellerSignatureResponse(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getBuyersRedirectTxSellerSignature().toByteArray());
    }

    @Override
    public String toString() {
        return "BuyersRedirectSellerSignatureResponse{" +
                "\r\n     senderNodeAddress=" + senderNodeAddress +
                ",\r\n     buyersRedirectTxSellerSignature=" + Utilities.bytesAsHexString(buyersRedirectTxSellerSignature) +
                "\r\n} " + super.toString();
    }
}
