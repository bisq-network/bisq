/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.arbitration.messages;

import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.core.arbitration.Dispute;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class OpenNewDisputeMessage extends DisputeMessage {
    private final Dispute dispute;
    private final NodeAddress senderNodeAddress;

    public OpenNewDisputeMessage(Dispute dispute,
                                 NodeAddress senderNodeAddress,
                                 String uid) {
        super(uid);
        this.dispute = dispute;
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setOpenNewDisputeMessage(PB.OpenNewDisputeMessage.newBuilder()
                        .setUid(getUid())
                        .setDispute(dispute.toProtoMessage())
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage()))
                .build();
    }

    public static OpenNewDisputeMessage fromProto(PB.OpenNewDisputeMessage proto, CoreProtoResolver coreProtoResolver) {
        return new OpenNewDisputeMessage(Dispute.fromProto(proto.getDispute(), coreProtoResolver),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid());
    }
}
