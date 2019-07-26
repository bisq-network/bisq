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

package bisq.core.dao.monitoring.network.messages;

import bisq.core.dao.monitoring.model.ProposalStateHash;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class NewProposalStateHashMessage extends NewStateHashMessage<ProposalStateHash> {
    public NewProposalStateHashMessage(ProposalStateHash proposalStateHash) {
        super(proposalStateHash, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private NewProposalStateHashMessage(ProposalStateHash proposalStateHash, int messageVersion) {
        super(proposalStateHash, messageVersion);
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setNewProposalStateHashMessage(protobuf.NewProposalStateHashMessage.newBuilder()
                        .setStateHash(stateHash.toProtoMessage()))
                .build();
    }

    public static NetworkEnvelope fromProto(protobuf.NewProposalStateHashMessage proto, int messageVersion) {
        return new NewProposalStateHashMessage(ProposalStateHash.fromProto(proto.getStateHash()), messageVersion);
    }

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.DAO_STATE);
    }
}
