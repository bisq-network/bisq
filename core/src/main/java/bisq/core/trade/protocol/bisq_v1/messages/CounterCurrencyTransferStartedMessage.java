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

package bisq.core.trade.protocol.bisq_v1.messages;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class CounterCurrencyTransferStartedMessage extends TradeMailboxMessage {
    private final String buyerPayoutAddress;
    private final NodeAddress senderNodeAddress;
    private final byte[] buyerSignature;
    @Nullable
    private final String counterCurrencyTxId;

    // Added after v1.3.7
    // We use that for the XMR txKey but want to keep it generic to be flexible for data of other payment methods or assets.
    @Nullable
    private String counterCurrencyExtraData;

    public CounterCurrencyTransferStartedMessage(String tradeId,
                                                 String buyerPayoutAddress,
                                                 NodeAddress senderNodeAddress,
                                                 byte[] buyerSignature,
                                                 @Nullable String counterCurrencyTxId,
                                                 @Nullable String counterCurrencyExtraData,
                                                 String uid) {
        this(tradeId,
                buyerPayoutAddress,
                senderNodeAddress,
                buyerSignature,
                counterCurrencyTxId,
                counterCurrencyExtraData,
                uid,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CounterCurrencyTransferStartedMessage(String tradeId,
                                                  String buyerPayoutAddress,
                                                  NodeAddress senderNodeAddress,
                                                  byte[] buyerSignature,
                                                  @Nullable String counterCurrencyTxId,
                                                  @Nullable String counterCurrencyExtraData,
                                                  String uid,
                                                  int messageVersion) {
        super(messageVersion, tradeId, uid);
        this.buyerPayoutAddress = buyerPayoutAddress;
        this.senderNodeAddress = senderNodeAddress;
        this.buyerSignature = buyerSignature;
        this.counterCurrencyTxId = counterCurrencyTxId;
        this.counterCurrencyExtraData = counterCurrencyExtraData;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        final protobuf.CounterCurrencyTransferStartedMessage.Builder builder = protobuf.CounterCurrencyTransferStartedMessage.newBuilder();
        builder.setTradeId(tradeId)
                .setBuyerPayoutAddress(buyerPayoutAddress)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setBuyerSignature(ByteString.copyFrom(buyerSignature))
                .setUid(uid);

        Optional.ofNullable(counterCurrencyTxId).ifPresent(e -> builder.setCounterCurrencyTxId(counterCurrencyTxId));
        Optional.ofNullable(counterCurrencyExtraData).ifPresent(e -> builder.setCounterCurrencyExtraData(counterCurrencyExtraData));

        return getNetworkEnvelopeBuilder().setCounterCurrencyTransferStartedMessage(builder).build();
    }

    public static CounterCurrencyTransferStartedMessage fromProto(protobuf.CounterCurrencyTransferStartedMessage proto,
                                                                  int messageVersion) {
        return new CounterCurrencyTransferStartedMessage(proto.getTradeId(),
                proto.getBuyerPayoutAddress(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getBuyerSignature().toByteArray(),
                ProtoUtil.stringOrNullFromProto(proto.getCounterCurrencyTxId()),
                ProtoUtil.stringOrNullFromProto(proto.getCounterCurrencyExtraData()),
                proto.getUid(),
                messageVersion);
    }

    @Override
    public String toString() {
        return "CounterCurrencyTransferStartedMessage{" +
                "\n     buyerPayoutAddress='" + buyerPayoutAddress + '\'' +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     counterCurrencyTxId=" + counterCurrencyTxId +
                ",\n     counterCurrencyExtraData=" + counterCurrencyExtraData +
                ",\n     uid='" + uid + '\'' +
                ",\n     buyerSignature=" + Utilities.bytesAsHexString(buyerSignature) +
                "\n} " + super.toString();
    }
}
