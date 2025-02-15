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

import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.protocol.bisq_v1.messages.TradeMailboxMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Getter;

// It is the last message in the take offer phase. We use MailboxMessage instead of DirectMessage to add more tolerance
// in case of network issues and as the message does not trigger further protocol execution.
@EqualsAndHashCode(callSuper = true)
@Getter
public class DepositTxAndSellerPaymentAccountMessage extends TradeMailboxMessage {
    private final NodeAddress senderNodeAddress;
    private final byte[] depositTx;
    private final PaymentAccountPayload sellerPaymentAccountPayload;

    public DepositTxAndSellerPaymentAccountMessage(String uid,
                                                   String tradeId,
                                                   NodeAddress senderNodeAddress,
                                                   byte[] depositTx,
                                                   PaymentAccountPayload sellerPaymentAccountPayload) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                depositTx,
                sellerPaymentAccountPayload);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DepositTxAndSellerPaymentAccountMessage(int messageVersion,
                                                    String uid,
                                                    String tradeId,
                                                    NodeAddress senderNodeAddress,
                                                    byte[] depositTx,
                                                    PaymentAccountPayload sellerPaymentAccountPayload) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.depositTx = depositTx;
        this.sellerPaymentAccountPayload = sellerPaymentAccountPayload;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.DepositTxAndSellerPaymentAccountMessage.Builder builder = protobuf.DepositTxAndSellerPaymentAccountMessage.newBuilder()
                .setUid(uid)
                .setTradeId(tradeId)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setDepositTx(ByteString.copyFrom(depositTx))
                .setSellerPaymentAccountPayload((protobuf.PaymentAccountPayload) sellerPaymentAccountPayload.toProtoMessage());

        return getNetworkEnvelopeBuilder().setDepositTxAndSellerPaymentAccountMessage(builder).build();
    }

    public static DepositTxAndSellerPaymentAccountMessage fromProto(protobuf.DepositTxAndSellerPaymentAccountMessage proto,
                                                                    CoreProtoResolver coreProtoResolver,
                                                                    int messageVersion) {
        return new DepositTxAndSellerPaymentAccountMessage(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getDepositTx().toByteArray(),
                coreProtoResolver.fromProto(proto.getSellerPaymentAccountPayload()));
    }

    @Override
    public String toString() {
        return "DepositTxAndDelayedPayoutTxMessage{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     depositTx=" + Utilities.bytesAsHexString(depositTx) +
                "\n} " + super.toString();
    }
}
