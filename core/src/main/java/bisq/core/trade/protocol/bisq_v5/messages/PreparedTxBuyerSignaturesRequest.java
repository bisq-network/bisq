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

import bisq.common.util.Utilities;

import protobuf.NetworkEnvelope;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public class PreparedTxBuyerSignaturesRequest extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] buyersWarningTxSellerSignature;
    private final byte[] sellersWarningTxSellerSignature;
    private final byte[] buyersRedirectTxSellerSignature;
    private final byte[] sellersRedirectTxSellerSignature;

    public PreparedTxBuyerSignaturesRequest(int messageVersion,
                                            String tradeId,
                                            String uid,
                                            NodeAddress senderNodeAddress,
                                            byte[] buyersWarningTxSellerSignature,
                                            byte[] sellersWarningTxSellerSignature,
                                            byte[] buyersRedirectTxSellerSignature,
                                            byte[] sellersRedirectTxSellerSignature) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.buyersWarningTxSellerSignature = buyersWarningTxSellerSignature;
        this.sellersWarningTxSellerSignature = sellersWarningTxSellerSignature;
        this.buyersRedirectTxSellerSignature = buyersRedirectTxSellerSignature;
        this.sellersRedirectTxSellerSignature = sellersRedirectTxSellerSignature;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.PreparedTxBuyerSignaturesRequest.Builder builder = protobuf.PreparedTxBuyerSignaturesRequest.newBuilder()
                .setTradeId(tradeId)
                .setUid(uid)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setBuyersWarningTxSellerSignature(ByteString.copyFrom(buyersWarningTxSellerSignature))
                .setSellersWarningTxSellerSignature(ByteString.copyFrom(sellersWarningTxSellerSignature))
                .setBuyersRedirectTxSellerSignature(ByteString.copyFrom(buyersRedirectTxSellerSignature))
                .setSellersRedirectTxSellerSignature(ByteString.copyFrom(sellersRedirectTxSellerSignature));

        return getNetworkEnvelopeBuilder()
                .setPreparedTxBuyerSignaturesRequest(builder)
                .build();
    }

    public static PreparedTxBuyerSignaturesRequest fromProto(protobuf.PreparedTxBuyerSignaturesRequest proto,
                                                             int messageVersion) {
        return new PreparedTxBuyerSignaturesRequest(messageVersion,
                proto.getTradeId(),
                proto.getUid(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getBuyersWarningTxSellerSignature().toByteArray(),
                proto.getSellersWarningTxSellerSignature().toByteArray(),
                proto.getBuyersRedirectTxSellerSignature().toByteArray(),
                proto.getSellersRedirectTxSellerSignature().toByteArray());
    }

    @Override
    public String toString() {
        return "PreparedTxBuyerSignaturesRequest{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     buyersWarningTxSellerSignature=" + Utilities.bytesAsHexString(buyersWarningTxSellerSignature) +
                ",\n     sellersWarningTxSellerSignature=" + Utilities.bytesAsHexString(sellersWarningTxSellerSignature) +
                ",\n     buyersRedirectTxSellerSignature=" + Utilities.bytesAsHexString(buyersRedirectTxSellerSignature) +
                ",\n     sellersRedirectTxSellerSignature=" + Utilities.bytesAsHexString(sellersRedirectTxSellerSignature) +
                "\n}" + super.toString();
    }
}
