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
public class PreparedTxBuyerSignaturesMessage extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] depositTxWithBuyerWitnesses;
    private final byte[] buyersWarningTxBuyerSignature;
    private final byte[] sellersWarningTxBuyerSignature;
    private final byte[] buyersRedirectTxBuyerSignature;
    private final byte[] sellersRedirectTxBuyerSignature;

    public PreparedTxBuyerSignaturesMessage(int messageVersion,
                                            String tradeId,
                                            String uid,
                                            NodeAddress senderNodeAddress,
                                            byte[] depositTxWithBuyerWitnesses,
                                            byte[] buyersWarningTxBuyerSignature,
                                            byte[] sellersWarningTxBuyerSignature,
                                            byte[] buyersRedirectTxBuyerSignature,
                                            byte[] sellersRedirectTxBuyerSignature) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.depositTxWithBuyerWitnesses = depositTxWithBuyerWitnesses;
        this.buyersWarningTxBuyerSignature = buyersWarningTxBuyerSignature;
        this.sellersWarningTxBuyerSignature = sellersWarningTxBuyerSignature;
        this.buyersRedirectTxBuyerSignature = buyersRedirectTxBuyerSignature;
        this.sellersRedirectTxBuyerSignature = sellersRedirectTxBuyerSignature;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.PreparedTxBuyerSignaturesMessage.Builder builder = protobuf.PreparedTxBuyerSignaturesMessage.newBuilder()
                .setTradeId(tradeId)
                .setUid(uid)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setDepositTxWithBuyerWitnesses(ByteString.copyFrom(depositTxWithBuyerWitnesses))
                .setBuyersWarningTxBuyerSignature(ByteString.copyFrom(buyersWarningTxBuyerSignature))
                .setSellersWarningTxBuyerSignature(ByteString.copyFrom(sellersWarningTxBuyerSignature))
                .setBuyersRedirectTxBuyerSignature(ByteString.copyFrom(buyersRedirectTxBuyerSignature))
                .setSellersRedirectTxBuyerSignature(ByteString.copyFrom(sellersRedirectTxBuyerSignature));

        return getNetworkEnvelopeBuilder()
                .setPreparedTxBuyerSignaturesMessage(builder)
                .build();
    }

    public static PreparedTxBuyerSignaturesMessage fromProto(protobuf.PreparedTxBuyerSignaturesMessage proto,
                                                             int messageVersion) {
        return new PreparedTxBuyerSignaturesMessage(messageVersion,
                proto.getTradeId(),
                proto.getUid(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDepositTxWithBuyerWitnesses().toByteArray(),
                proto.getBuyersWarningTxBuyerSignature().toByteArray(),
                proto.getSellersWarningTxBuyerSignature().toByteArray(),
                proto.getBuyersRedirectTxBuyerSignature().toByteArray(),
                proto.getSellersRedirectTxBuyerSignature().toByteArray());
    }

    @Override
    public String toString() {
        return "PreparedTxBuyerSignaturesMessage{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     depositTxWithBuyerWitnesses=" + Utilities.bytesAsHexString(depositTxWithBuyerWitnesses) +
                ",\n     buyersWarningTxBuyerSignature=" + Utilities.bytesAsHexString(buyersWarningTxBuyerSignature) +
                ",\n     sellersWarningTxBuyerSignature=" + Utilities.bytesAsHexString(sellersWarningTxBuyerSignature) +
                ",\n     buyersRedirectTxBuyerSignature=" + Utilities.bytesAsHexString(buyersRedirectTxBuyerSignature) +
                ",\n     sellersRedirectTxBuyerSignature=" + Utilities.bytesAsHexString(sellersRedirectTxBuyerSignature) +
                "\n}" + super.toString();
    }
}
