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

package bisq.core.dao.governance.blindvote.network.messages;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import lombok.EqualsAndHashCode;
import lombok.Getter;


// This message is sent only to full DAO nodes
@EqualsAndHashCode(callSuper = true)
@Getter
public final class RepublishGovernanceDataRequest extends NetworkEnvelope implements DirectMessage, CapabilityRequiringPayload {

    public RepublishGovernanceDataRequest() {
        this(Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RepublishGovernanceDataRequest(int messageVersion) {
        super(messageVersion);
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setRepublishGovernanceDataRequest(protobuf.RepublishGovernanceDataRequest.newBuilder())
                .build();
    }

    public static NetworkEnvelope fromProto(protobuf.RepublishGovernanceDataRequest proto, int messageVersion) {
        return new RepublishGovernanceDataRequest(messageVersion);
    }

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.DAO_FULL_NODE);
    }

    @Override
    public String toString() {
        return "RepublishGovernanceDataRequest{" +
                "\n} " + super.toString();
    }
}
