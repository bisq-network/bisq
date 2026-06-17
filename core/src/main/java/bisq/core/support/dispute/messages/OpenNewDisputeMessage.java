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

import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendersSignaturePubKeyProvidingPayload;

import bisq.common.app.Version;
import bisq.common.crypto.PubKeyRing;

import java.security.PublicKey;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
@Value
public final class OpenNewDisputeMessage extends DisputeMessage implements SendersSignaturePubKeyProvidingPayload {
    private final Dispute dispute;
    private final NodeAddress senderNodeAddress;
    @Nullable
    private final PublicKey senderSignaturePubKey;

    public OpenNewDisputeMessage(Dispute dispute,
                                 NodeAddress senderNodeAddress,
                                 String uid,
                                 SupportType supportType) {
        this(dispute,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion(),
                supportType,
                getDisputeOpenerSignaturePubKey(dispute));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private OpenNewDisputeMessage(Dispute dispute,
                                  NodeAddress senderNodeAddress,
                                  String uid,
                                  int messageVersion,
                                  SupportType supportType,
                                  @Nullable PublicKey senderSignaturePubKey) {
        super(messageVersion, uid, supportType);
        this.dispute = dispute;
        this.senderNodeAddress = senderNodeAddress;
        this.senderSignaturePubKey = senderSignaturePubKey;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.OpenNewDisputeMessage.Builder builder = protobuf.OpenNewDisputeMessage.newBuilder()
                .setUid(uid)
                .setDispute(dispute.toProtoMessage())
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setType(SupportType.toProtoMessage(supportType));
        if (senderSignaturePubKey != null) {
            builder.setSenderSignaturePubKey(senderSignaturePubKeyToProto(senderSignaturePubKey));
        }
        return getNetworkEnvelopeBuilder()
                .setOpenNewDisputeMessage(builder)
                .build();
    }

    public static OpenNewDisputeMessage fromProto(protobuf.OpenNewDisputeMessage proto,
                                                  CoreProtoResolver coreProtoResolver,
                                                  int messageVersion) {
        return new OpenNewDisputeMessage(Dispute.fromProto(proto.getDispute(), coreProtoResolver),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion,
                SupportType.fromProto(proto.getType()),
                senderSignaturePubKeyFromProto(proto.getSenderSignaturePubKey()));
    }

    @Override
    public String getTradeId() {
        return dispute.getTradeId();
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

    @Nullable
    private static PublicKey getDisputeOpenerSignaturePubKey(@Nullable Dispute dispute) {
        Contract contract = dispute == null ? null : dispute.getContract();
        if (contract == null) {
            return null;
        }

        PubKeyRing senderPubKeyRing = dispute.isDisputeOpenerIsBuyer() ?
                contract.getBuyerPubKeyRing() :
                contract.getSellerPubKeyRing();
        return senderPubKeyRing == null ? null : senderPubKeyRing.getSignaturePubKey();
    }

    @Override
    public String toString() {
        return "OpenNewDisputeMessage{" +
                "\n     dispute=" + dispute +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     senderSignaturePubKey=" + senderSignaturePubKey +
                ",\n     OpenNewDisputeMessage.uid='" + uid + '\'' +
                ",\n     messageVersion=" + messageVersion +
                ",\n     supportType=" + supportType +
                "\n} " + super.toString();
    }
}
