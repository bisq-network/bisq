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

import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.proto.CoreProtoResolver;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

// It is the last message in the take offer phase. We use MailboxMessage instead of DirectMessage to add more tolerance
// in case of network issues and as the message does not trigger further protocol execution.
@EqualsAndHashCode(callSuper = true)
@Getter
public final class DepositTxAndDelayedPayoutTxMessage extends TradeMailboxMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] depositTx;
    private final byte[] delayedPayoutTx;

    // Added at v1.7.0
    @Nullable
    private final PaymentAccountPayload sellerPaymentAccountPayload;

    public DepositTxAndDelayedPayoutTxMessage(String uid,
                                              String tradeId,
                                              NodeAddress senderNodeAddress,
                                              byte[] depositTx,
                                              byte[] delayedPayoutTx,
                                              @Nullable PaymentAccountPayload sellerPaymentAccountPayload) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                depositTx,
                delayedPayoutTx,
                sellerPaymentAccountPayload);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DepositTxAndDelayedPayoutTxMessage(int messageVersion,
                                               String uid,
                                               String tradeId,
                                               NodeAddress senderNodeAddress,
                                               byte[] depositTx,
                                               byte[] delayedPayoutTx,
                                               @Nullable PaymentAccountPayload sellerPaymentAccountPayload) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.depositTx = depositTx;
        this.delayedPayoutTx = delayedPayoutTx;
        this.sellerPaymentAccountPayload = sellerPaymentAccountPayload;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.DepositTxAndDelayedPayoutTxMessage.Builder builder = protobuf.DepositTxAndDelayedPayoutTxMessage.newBuilder()
                .setUid(uid)
                .setTradeId(tradeId)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setDepositTx(ByteString.copyFrom(depositTx))
                .setDelayedPayoutTx(ByteString.copyFrom(delayedPayoutTx));

        Optional.ofNullable(sellerPaymentAccountPayload)
                .ifPresent(e -> builder.setSellerPaymentAccountPayload((protobuf.PaymentAccountPayload) sellerPaymentAccountPayload.toProtoMessage()));

        return getNetworkEnvelopeBuilder().setDepositTxAndDelayedPayoutTxMessage(builder).build();
    }

    public static DepositTxAndDelayedPayoutTxMessage fromProto(protobuf.DepositTxAndDelayedPayoutTxMessage proto,
                                                               CoreProtoResolver coreProtoResolver,
                                                               int messageVersion) {
        PaymentAccountPayload sellerPaymentAccountPayload = proto.hasSellerPaymentAccountPayload() ?
                coreProtoResolver.fromProto(proto.getSellerPaymentAccountPayload()) : null;
        return new DepositTxAndDelayedPayoutTxMessage(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDepositTx().toByteArray(),
                proto.getDelayedPayoutTx().toByteArray(),
                sellerPaymentAccountPayload);
    }

    @Override
    public String toString() {
        return "DepositTxAndDelayedPayoutTxMessage{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     depositTx=" + Utilities.bytesAsHexString(depositTx) +
                ",\n     delayedPayoutTx=" + Utilities.bytesAsHexString(delayedPayoutTx) +
                "\n} " + super.toString();
    }
}
