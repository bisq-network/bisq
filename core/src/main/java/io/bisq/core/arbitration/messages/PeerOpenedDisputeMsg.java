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

import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;
import io.bisq.core.arbitration.Dispute;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString
public final class PeerOpenedDisputeMsg extends DisputeMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final Dispute dispute;
    private final NodeAddress myNodeAddress;

    public PeerOpenedDisputeMsg(Dispute dispute, NodeAddress myNodeAddress, String uid) {
        super(uid);
        this.dispute = dispute;
        this.myNodeAddress = myNodeAddress;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return myNodeAddress;
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Msg.getEnv();
        return baseEnvelope.setPeerOpenedDisputeMessage(PB.PeerOpenedDisputeMessage.newBuilder()
                .setDispute(dispute.toProto())
                .setMyNodeAddress(myNodeAddress.toProto())
                .setUid(getUID())).build();
    }
}
