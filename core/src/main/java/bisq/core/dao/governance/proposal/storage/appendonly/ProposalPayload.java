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

package bisq.core.dao.governance.proposal.storage.appendonly;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.Hash;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Wrapper for proposal to be stored in the append-only ProposalStore storage.
 * Data size: with typical proposal about 272 bytes
 */
@Immutable
@Slf4j
@Value
public class ProposalPayload implements PersistableNetworkPayload, PersistableEnvelope,
        CapabilityRequiringPayload, ConsensusCritical {
    private final Proposal proposal;
    protected final byte[] hash;        // 20 byte

    public ProposalPayload(Proposal proposal) {
        this(proposal, Hash.getRipemd160hash(proposal.toProtoMessage().toByteArray()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ProposalPayload(Proposal proposal, byte[] hash) {
        this.proposal = proposal;
        this.hash = hash;
    }

    private PB.ProposalPayload.Builder getProposalBuilder() {
        return PB.ProposalPayload.newBuilder()
                .setProposal(proposal.toProtoMessage())
                .setHash(ByteString.copyFrom(hash));
    }

    @Override
    public PB.PersistableNetworkPayload toProtoMessage() {
        return PB.PersistableNetworkPayload.newBuilder()
                .setProposalPayload(getProposalBuilder())
                .build();
    }

    public PB.ProposalPayload toProtoProposalPayload() {
        return getProposalBuilder().build();
    }


    public static ProposalPayload fromProto(PB.ProposalPayload proto) {
        return new ProposalPayload(Proposal.fromProto(proto.getProposal()),
                proto.getHash().toByteArray());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistableNetworkPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean verifyHashSize() {
        return hash.length == 20;
    }

    @Override
    public byte[] getHash() {
        return hash;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CapabilityRequiringPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.PROPOSAL);
    }

    @Override
    public String toString() {
        return "ProposalPayload{" +
                "\n     proposal=" + proposal +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}
