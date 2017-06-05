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
import io.bisq.core.arbitration.DisputeResult;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

@Value
@EqualsAndHashCode(callSuper = true)
public final class DisputeResultMessage extends DisputeMessage {
    private final DisputeResult disputeResult;
    private final NodeAddress senderNodeAddress;

    public DisputeResultMessage(DisputeResult disputeResult,
                                NodeAddress senderNodeAddress,
                                String uid) {
        super(uid);
        this.disputeResult = disputeResult;
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setDisputeResultMessage(PB.DisputeResultMessage.newBuilder()
                        .setUid(getUid())
                        .setDisputeResult(disputeResult.toProtoMessage())
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage()))
                .build();
    }

    public static DisputeResultMessage fromProto(PB.DisputeResultMessage proto) {
        checkArgument(!proto.equals(proto.getDefaultInstanceForType()), "proto must be set (we received default instance)");
        final Optional<DisputeResult> disputeResult = DisputeResult.fromProto(proto.getDisputeResult());
        checkArgument(disputeResult.isPresent(), "disputeResult i not present");
        return new DisputeResultMessage(disputeResult.get(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid());
    }
}
