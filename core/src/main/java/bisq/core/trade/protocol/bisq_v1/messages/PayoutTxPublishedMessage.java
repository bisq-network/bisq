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

import bisq.core.account.sign.SignedWitness;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Optional;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class PayoutTxPublishedMessage extends TradeMailboxMessage {
    private final byte[] payoutTx;
    private final NodeAddress senderNodeAddress;

    // Added in v1.4.0
    @Nullable
    private final SignedWitness signedWitness;

    public PayoutTxPublishedMessage(String tradeId,
                                    byte[] payoutTx,
                                    NodeAddress senderNodeAddress,
                                    @Nullable SignedWitness signedWitness) {
        this(tradeId,
                payoutTx,
                senderNodeAddress,
                signedWitness,
                UUID.randomUUID().toString(),
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PayoutTxPublishedMessage(String tradeId,
                                     byte[] payoutTx,
                                     NodeAddress senderNodeAddress,
                                     @Nullable SignedWitness signedWitness,
                                     String uid,
                                     int messageVersion) {
        super(messageVersion, tradeId, uid);
        this.payoutTx = payoutTx;
        this.senderNodeAddress = senderNodeAddress;
        this.signedWitness = signedWitness;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.PayoutTxPublishedMessage.Builder builder = protobuf.PayoutTxPublishedMessage.newBuilder()
                .setTradeId(tradeId)
                .setPayoutTx(ByteString.copyFrom(payoutTx))
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setUid(uid);
        Optional.ofNullable(signedWitness).ifPresent(signedWitness -> builder.setSignedWitness(signedWitness.toProtoSignedWitness()));
        return getNetworkEnvelopeBuilder().setPayoutTxPublishedMessage(builder).build();
    }

    public static NetworkEnvelope fromProto(protobuf.PayoutTxPublishedMessage proto, int messageVersion) {
        // There is no method to check for a nullable non-primitive data type object but we know that all fields
        // are empty/null, so we check for the signature to see if we got a valid signedWitness.
        protobuf.SignedWitness protoSignedWitness = proto.getSignedWitness();
        SignedWitness signedWitness = !protoSignedWitness.getSignature().isEmpty() ?
                SignedWitness.fromProto(protoSignedWitness) :
                null;
        return new PayoutTxPublishedMessage(proto.getTradeId(),
                proto.getPayoutTx().toByteArray(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                signedWitness,
                proto.getUid(),
                messageVersion);
    }

    @Override
    public String toString() {
        return "PayoutTxPublishedMessage{" +
                "\n     payoutTx=" + Utilities.bytesAsHexString(payoutTx) +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     signedWitness=" + signedWitness +
                "\n} " + super.toString();
    }
}
