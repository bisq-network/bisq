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

package bisq.core.support.dispute.messages;

import bisq.core.support.SupportType;
import bisq.core.support.dispute.DisputeResult;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendersSignaturePubKeyProvidingPayload;

import bisq.common.app.Version;

import java.security.PublicKey;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Value
@EqualsAndHashCode(callSuper = true)
public final class DisputeResultMessage extends DisputeMessage implements SendersSignaturePubKeyProvidingPayload {
    private final DisputeResult disputeResult;
    private final NodeAddress senderNodeAddress;
    @Nullable
    private final PublicKey senderSignaturePubKey;

    public DisputeResultMessage(DisputeResult disputeResult,
                                NodeAddress senderNodeAddress,
                                String uid,
                                SupportType supportType) {
        this(disputeResult,
                senderNodeAddress,
                uid,
                supportType,
                null);
    }

    public DisputeResultMessage(DisputeResult disputeResult,
                                NodeAddress senderNodeAddress,
                                String uid,
                                SupportType supportType,
                                @Nullable PublicKey senderSignaturePubKey) {
        this(disputeResult,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion(),
                supportType,
                senderSignaturePubKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DisputeResultMessage(DisputeResult disputeResult,
                                 NodeAddress senderNodeAddress,
                                 String uid,
                                 int messageVersion,
                                 SupportType supportType,
                                 @Nullable PublicKey senderSignaturePubKey) {
        super(messageVersion, uid, supportType);
        this.disputeResult = disputeResult;
        this.senderNodeAddress = senderNodeAddress;
        this.senderSignaturePubKey = senderSignaturePubKey;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.DisputeResultMessage.Builder builder = protobuf.DisputeResultMessage.newBuilder()
                .setDisputeResult(disputeResult.toProtoMessage())
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setUid(uid)
                .setType(SupportType.toProtoMessage(supportType));
        if (senderSignaturePubKey != null) {
            builder.setSenderSignaturePubKey(senderSignaturePubKeyToProto(senderSignaturePubKey));
        }
        return getNetworkEnvelopeBuilder()
                .setDisputeResultMessage(builder)
                .build();
    }

    public static DisputeResultMessage fromProto(protobuf.DisputeResultMessage proto, int messageVersion) {
        checkArgument(proto.hasDisputeResult(), "DisputeResult must be set");
        return new DisputeResultMessage(DisputeResult.fromProto(proto.getDisputeResult()),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion,
                SupportType.fromProto(proto.getType()),
                senderSignaturePubKeyFromProto(proto.getSenderSignaturePubKey()));
    }

    @Override
    public String getTradeId() {
        return disputeResult.getTradeId();
    }

    @Override
    @Nullable
    public PublicKey getSenderSignaturePubKey() {
        return senderSignaturePubKey;
    }

    @Override
    public boolean isSenderSignaturePubKeyRequired() {
        return isSenderSignaturePubKeyValidationRequired();
    }

    @Override
    public String toString() {
        return "DisputeResultMessage{" +
                "\n     disputeResult=" + disputeResult +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     senderSignaturePubKey=" + senderSignaturePubKey +
                ",\n     DisputeResultMessage.uid='" + uid + '\'' +
                ",\n     messageVersion=" + messageVersion +
                ",\n     supportType=" + supportType +
                "\n} " + super.toString();
    }
}
