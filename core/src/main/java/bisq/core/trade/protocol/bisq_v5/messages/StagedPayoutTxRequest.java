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
public final class StagedPayoutTxRequest extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] sellersWarningTx;
    private final byte[] sellersWarningTxSellerSignature;
    private final byte[] sellersRedirectionTx;
    private final byte[] sellersRedirectionTxSellerSignature;
    private final byte[] buyersWarningTxSellerSignature;

    public StagedPayoutTxRequest(String uid,
                                 String tradeId,
                                 NodeAddress senderNodeAddress,
                                 byte[] sellersWarningTx,
                                 byte[] sellersWarningTxSellerSignature,
                                 byte[] sellersRedirectionTx,
                                 byte[] sellersRedirectionTxSellerSignature,
                                 byte[] buyersWarningTxSellerSignature) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                sellersWarningTx,
                sellersWarningTxSellerSignature,
                sellersRedirectionTx,
                sellersRedirectionTxSellerSignature,
                buyersWarningTxSellerSignature);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private StagedPayoutTxRequest(int messageVersion,
                                  String uid,
                                  String tradeId,
                                  NodeAddress senderNodeAddress,
                                  byte[] sellersWarningTx,
                                  byte[] sellersWarningTxSellerSignature,
                                  byte[] sellersRedirectionTx,
                                  byte[] sellersRedirectionTxSellerSignature,
                                  byte[] buyersWarningTxSellerSignature) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.sellersWarningTx = sellersWarningTx;
        this.sellersWarningTxSellerSignature = sellersWarningTxSellerSignature;
        this.sellersRedirectionTx = sellersRedirectionTx;
        this.sellersRedirectionTxSellerSignature = sellersRedirectionTxSellerSignature;
        this.buyersWarningTxSellerSignature = buyersWarningTxSellerSignature;
    }


//    @Override
//    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
//        return getNetworkEnvelopeBuilder()
//                .setStagedPayoutTxRequest(protobuf.StagedPayoutTxRequest.newBuilder()
//                        .setUid(uid)
//                        .setTradeId(tradeId)
//                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
//                        .setSellersWarningTx(ByteString.copyFrom(sellersWarningTx))
//                        .setSellersWarningTxSellerSignature(ByteString.copyFrom(sellersWarningTxSellerSignature))
//                        .setSellersRedirectionTx(ByteString.copyFrom(sellersRedirectionTx))
//                        .setSellersRedirectionTxSellerSignature(ByteString.copyFrom(sellersRedirectionTxSellerSignature))
//                        .setBuyersWarningTxSellerSignature(ByteString.copyFrom(buyersWarningTxSellerSignature)))
//                .build();
//    }

//    public static StagedPayoutTxRequest fromProto(protobuf.StagedPayoutTxRequest proto,
//                                                  int messageVersion) {
//        return new StagedPayoutTxRequest(messageVersion,
//                proto.getUid(),
//                proto.getTradeId(),
//                NodeAddress.fromProto(proto.getSenderNodeAddress()),
//                proto.getSellersWarningTx().toByteArray(),
//                proto.getSellersWarningTxSellerSignature().toByteArray(),
//                proto.getSellersRedirectionTx().toByteArray(),
//                proto.getSellersRedirectionTxSellerSignature().toByteArray(),
//                proto.getBuyersWarningTxSellerSignature().toByteArray());
//    }

    @Override
    public String toString() {
        return "StagedPayoutTxRequest{" +
                "\r\n     senderNodeAddress=" + senderNodeAddress +
                ",\r\n     sellersWarningTx=" + Utilities.bytesAsHexString(sellersWarningTx) +
                ",\r\n     sellersWarningTxSellerSignature=" + Utilities.bytesAsHexString(sellersWarningTxSellerSignature) +
                ",\r\n     sellersRedirectionTx=" + Utilities.bytesAsHexString(sellersRedirectionTx) +
                ",\r\n     sellersRedirectionTxSellerSignature=" + Utilities.bytesAsHexString(sellersRedirectionTxSellerSignature) +
                ",\r\n     buyersWarningTxSellerSignature=" + Utilities.bytesAsHexString(buyersWarningTxSellerSignature) +
                "\r\n} " + super.toString();
    }
}
