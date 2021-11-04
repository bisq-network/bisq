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

import lombok.EqualsAndHashCode;
import lombok.Getter;

// Added at v1.7.0
@EqualsAndHashCode(callSuper = true)
@Getter
public final class ShareBuyerPaymentAccountMessage extends TradeMailboxMessage {
    private final NodeAddress senderNodeAddress;
    private final PaymentAccountPayload buyerPaymentAccountPayload;

    public ShareBuyerPaymentAccountMessage(String uid,
                                           String tradeId,
                                           NodeAddress senderNodeAddress,
                                           PaymentAccountPayload buyerPaymentAccountPayload) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                buyerPaymentAccountPayload);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ShareBuyerPaymentAccountMessage(int messageVersion,
                                            String uid,
                                            String tradeId,
                                            NodeAddress senderNodeAddress,
                                            PaymentAccountPayload buyerPaymentAccountPayload) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.buyerPaymentAccountPayload = buyerPaymentAccountPayload;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder().setShareBuyerPaymentAccountMessage(
                protobuf.ShareBuyerPaymentAccountMessage.newBuilder()
                        .setUid(uid)
                        .setTradeId(tradeId)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setBuyerPaymentAccountPayload((protobuf.PaymentAccountPayload) buyerPaymentAccountPayload.toProtoMessage()))
                .build();
    }

    public static ShareBuyerPaymentAccountMessage fromProto(protobuf.ShareBuyerPaymentAccountMessage proto,
                                                            CoreProtoResolver coreProtoResolver,
                                                            int messageVersion) {
        PaymentAccountPayload buyerPaymentAccountPayload = proto.hasBuyerPaymentAccountPayload() ?
                coreProtoResolver.fromProto(proto.getBuyerPaymentAccountPayload()) : null;
        return new ShareBuyerPaymentAccountMessage(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                buyerPaymentAccountPayload);
    }

    @Override
    public String toString() {
        return "ShareBuyerPaymentAccountMessage{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                "\n} " + super.toString();
    }
}
