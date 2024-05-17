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

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class BuyersRedirectSellerSignatureRequest extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] sellersWarningTxBuyerSignature;
    private final byte[] sellersRedirectTxBuyerSignature;
    private final byte[] buyersRedirectTx;
    private final byte[] buyersRedirectTxBuyerSignature;

    public BuyersRedirectSellerSignatureRequest(String uid,
                                                String tradeId,
                                                NodeAddress senderNodeAddress,
                                                byte[] sellersWarningTxBuyerSignature,
                                                byte[] sellersRedirectTxBuyerSignature,
                                                byte[] buyersRedirectTx,
                                                byte[] buyersRedirectTxBuyerSignature) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                sellersWarningTxBuyerSignature,
                sellersRedirectTxBuyerSignature,
                buyersRedirectTx,
                buyersRedirectTxBuyerSignature);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BuyersRedirectSellerSignatureRequest(int messageVersion,
                                                 String uid,
                                                 String tradeId,
                                                 NodeAddress senderNodeAddress,
                                                 byte[] sellersWarningTxBuyerSignature,
                                                 byte[] sellersRedirectTxBuyerSignature,
                                                 byte[] buyersRedirectTx,
                                                 byte[] buyersRedirectTxBuyerSignature) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.sellersWarningTxBuyerSignature = sellersWarningTxBuyerSignature;
        this.sellersRedirectTxBuyerSignature = sellersRedirectTxBuyerSignature;
        this.buyersRedirectTx = buyersRedirectTx;
        this.buyersRedirectTxBuyerSignature = buyersRedirectTxBuyerSignature;
    }

//    @Override
//    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
//        return getNetworkEnvelopeBuilder()
//                .setBuyersRedirectSellerSignatureRequest(protobuf.BuyersRedirectSellerSignatureRequest.newBuilder()
//                        .setUid(uid)
//                        .setTradeId(tradeId)
//                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
//                        .setSellersWarningTxBuyerSignature(ByteString.copyFrom(sellersWarningTxBuyerSignature))
//                        .setSellersRedirectTxBuyerSignature(ByteString.copyFrom(sellersRedirectTxBuyerSignature))
//                        .setBuyersRedirectTx(ByteString.copyFrom(buyersRedirectTx))
//                        .setBuyersRedirectTxBuyerSignature(ByteString.copyFrom(buyersRedirectTxBuyerSignature))
//                )
//                .build();
//    }

//    public static BuyersRedirectSellerSignatureRequest fromProto(protobuf.BuyersRedirectSellerSignatureRequest proto,
//                                                                 int messageVersion) {
//        return new BuyersRedirectSellerSignatureRequest(messageVersion,
//                proto.getUid(),
//                proto.getTradeId(),
//                NodeAddress.fromProto(proto.getSenderNodeAddress()),
//                proto.getSellersWarningTxBuyerSignature().toByteArray(),
//                proto.getSellersRedirectTxBuyerSignature().toByteArray(),
//                proto.getBuyersRedirectTx().toByteArray(),
//                proto.getBuyersRedirectTxBuyerSignature().toByteArray());
//    }

    @Override
    public String toString() {
        return "BuyersRedirectSellerSignatureRequest{" +
                "\r\n     senderNodeAddress=" + senderNodeAddress +
                ",\r\n     sellersWarningTxBuyerSignature=" + Utilities.bytesAsHexString(sellersWarningTxBuyerSignature) +
                ",\r\n     sellersRedirectTxBuyerSignature=" + Utilities.bytesAsHexString(sellersRedirectTxBuyerSignature) +
                ",\r\n     buyersRedirectTx=" + Utilities.bytesAsHexString(buyersRedirectTx) +
                ",\r\n     buyersRedirectTxBuyerSignature=" + Utilities.bytesAsHexString(buyersRedirectTxBuyerSignature) +
                "\r\n} " + super.toString();
    }
}
