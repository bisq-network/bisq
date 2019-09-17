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

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public final class PeerOpenedDisputeMessage extends DisputeMessage {
    private final Dispute dispute;
    private final NodeAddress senderNodeAddress;

    public PeerOpenedDisputeMessage(Dispute dispute,
                                    NodeAddress senderNodeAddress,
                                    String uid,
                                    SupportType supportType) {
        this(dispute,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion(),
                supportType);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PeerOpenedDisputeMessage(Dispute dispute,
                                     NodeAddress senderNodeAddress,
                                     String uid,
                                     int messageVersion,
                                     SupportType supportType) {
        super(messageVersion, uid, supportType);
        this.dispute = dispute;
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setPeerOpenedDisputeMessage(protobuf.PeerOpenedDisputeMessage.newBuilder()
                        .setUid(uid)
                        .setDispute(dispute.toProtoMessage())
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setType(SupportType.toProtoMessage(supportType)))
                .build();
    }

    public static PeerOpenedDisputeMessage fromProto(protobuf.PeerOpenedDisputeMessage proto, CoreProtoResolver coreProtoResolver, int messageVersion) {
        return new PeerOpenedDisputeMessage(Dispute.fromProto(proto.getDispute(), coreProtoResolver),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion,
                SupportType.fromProto(proto.getType()));
    }

    @Override
    public String getTradeId() {
        return dispute.getTradeId();
    }

    @Override
    public String toString() {
        return "PeerOpenedDisputeMessage{" +
                "\n     dispute=" + dispute +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     PeerOpenedDisputeMessage.uid='" + uid + '\'' +
                ",\n     messageVersion=" + messageVersion +
                ",\n     supportType=" + supportType +
                "\n} " + super.toString();
    }
}
