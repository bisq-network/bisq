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

import bisq.network.p2p.storage.payload.InvalidPersistableNetworkPayloadException;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Arrays;

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
public class ProposalPayload implements PersistableNetworkPayload, ConsensusCritical {
    private final Proposal proposal;
    protected final byte[] hash;        // 20 byte

    public ProposalPayload(Proposal proposal) {
        this.proposal = proposal;
        this.hash = Hash.getRipemd160hash(proposal.encodeCanonical());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private protobuf.ProposalPayload.Builder getProposalBuilder() {
        return protobuf.ProposalPayload.newBuilder()
                .setProposal(proposal.toProtoMessage())
                .setHash(ByteString.copyFrom(hash));
    }

    @Override
    public protobuf.PersistableNetworkPayload toProtoMessage() {
        return protobuf.PersistableNetworkPayload.newBuilder()
                .setProposalPayload(getProposalBuilder())
                .build();
    }

    public protobuf.ProposalPayload toProtoProposalPayload() {
        return getProposalBuilder().build();
    }


    public static ProposalPayload fromProto(protobuf.ProposalPayload proto) {
        Proposal proposal = Proposal.fromProto(proto.getProposal());
        ProposalPayload proposalPayload = new ProposalPayload(proposal);

        // Verify that provided hash matches the hash created from the proposal data
        byte[] hashFromProto = proto.getHash().toByteArray();
        if (!Arrays.equals(hashFromProto, proposalPayload.getHash())) {
            throw new InvalidPersistableNetworkPayloadException("ProposalPayload hash field does not match proposal data. " +
                    "proposalTxId=" + proposal.getTxId() +
                    ", hashFromProto=" + Utilities.bytesAsHexString(hashFromProto) +
                    ", computedHash=" + Utilities.bytesAsHexString(proposalPayload.getHash()));
        }

        return proposalPayload;
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

    @Override
    public String toString() {
        return "ProposalPayload{" +
                "\n     proposal=" + proposal +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}
