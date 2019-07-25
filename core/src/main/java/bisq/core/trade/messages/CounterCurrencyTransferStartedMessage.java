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

package bisq.core.trade.messages;

import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
@Value
public final class CounterCurrencyTransferStartedMessage extends TradeMessage implements MailboxMessage {
    private final String buyerPayoutAddress;
    private final NodeAddress senderNodeAddress;
    private final byte[] buyerSignature;
    @Nullable
    private final String counterCurrencyTxId;

    public CounterCurrencyTransferStartedMessage(String tradeId,
                                                 String buyerPayoutAddress,
                                                 NodeAddress senderNodeAddress,
                                                 byte[] buyerSignature,
                                                 @Nullable String counterCurrencyTxId,
                                                 String uid) {
        this(tradeId,
                buyerPayoutAddress,
                senderNodeAddress,
                buyerSignature,
                counterCurrencyTxId,
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
                                                  String uid,
                                                  int messageVersion) {
        super(messageVersion, tradeId, uid);
        this.buyerPayoutAddress = buyerPayoutAddress;
        this.senderNodeAddress = senderNodeAddress;
        this.buyerSignature = buyerSignature;
        this.counterCurrencyTxId = counterCurrencyTxId;
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

        return getNetworkEnvelopeBuilder().setCounterCurrencyTransferStartedMessage(builder).build();
    }

    public static CounterCurrencyTransferStartedMessage fromProto(protobuf.CounterCurrencyTransferStartedMessage proto, int messageVersion) {
        return new CounterCurrencyTransferStartedMessage(proto.getTradeId(),
                proto.getBuyerPayoutAddress(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getBuyerSignature().toByteArray(),
                proto.getCounterCurrencyTxId().isEmpty() ? null : proto.getCounterCurrencyTxId(),
                proto.getUid(),
                messageVersion);
    }


    @Override
    public String toString() {
        return "CounterCurrencyTransferStartedMessage{" +
                "\n     buyerPayoutAddress='" + buyerPayoutAddress + '\'' +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     counterCurrencyTxId=" + counterCurrencyTxId +
                ",\n     uid='" + uid + '\'' +
                ",\n     buyerSignature=" + Utilities.bytesAsHexString(buyerSignature) +
                "\n} " + super.toString();
    }
}
