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

import io.bisq.common.network.NetworkEnvelope;
import io.bisq.core.arbitration.Dispute;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString
public final class PeerOpenedDisputeMessage extends DisputeMessage {
    public final Dispute dispute;
    private final NodeAddress myNodeAddress;

    public PeerOpenedDisputeMessage(Dispute dispute, NodeAddress myNodeAddress, String uid) {
        super(uid);
        this.dispute = dispute;
        this.myNodeAddress = myNodeAddress;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return myNodeAddress;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder msgBuilder = NetworkEnvelope.getDefaultBuilder();
        return msgBuilder.setPeerOpenedDisputeMessage(PB.PeerOpenedDisputeMessage.newBuilder()
                .setDispute(dispute.toProtoMessage())
                .setMyNodeAddress(myNodeAddress.toProtoMessage())
                .setUid(getUid())).build();
    }
}
