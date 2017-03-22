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

package io.bisq.protobuffer.message.arbitration;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.payload.arbitration.DisputeResult;
import io.bisq.protobuffer.payload.p2p.NodeAddress;

public final class DisputeResultMessage extends DisputeMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final DisputeResult disputeResult;
    private final NodeAddress myNodeAddress;

    public DisputeResultMessage(DisputeResult disputeResult, NodeAddress myNodeAddress) {
        this.disputeResult = disputeResult;
        this.myNodeAddress = myNodeAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeResultMessage)) return false;

        DisputeResultMessage that = (DisputeResultMessage) o;

        if (disputeResult != null ? !disputeResult.equals(that.disputeResult) : that.disputeResult != null)
            return false;
        return !(myNodeAddress != null ? !myNodeAddress.equals(that.myNodeAddress) : that.myNodeAddress != null);

    }

    @Override
    public int hashCode() {
        int result = disputeResult != null ? disputeResult.hashCode() : 0;
        result = 31 * result + (myNodeAddress != null ? myNodeAddress.hashCode() : 0);
        return result;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return myNodeAddress;
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        return baseEnvelope.setDisputeResultMessage(PB.DisputeResultMessage.newBuilder()
                .setDisputeResult(disputeResult.toProto())
                .setMyNodeAddress(myNodeAddress.toProto())
                .setUid(getUID()))
                .build();
    }
}
