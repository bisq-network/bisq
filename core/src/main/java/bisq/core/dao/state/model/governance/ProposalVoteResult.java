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

package bisq.core.dao.state.model.governance;

import bisq.core.dao.state.model.ImmutableDaoStateModel;
import bisq.core.encoding.canonical.Canonical;
import bisq.core.encoding.canonical.CanonicalEncoder;
import bisq.core.encoding.canonical.CanonicalSchema;

import bisq.common.proto.persistable.PersistablePayload;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

import static com.google.common.base.Preconditions.checkArgument;

@Immutable
@Value
@Slf4j
public class ProposalVoteResult implements PersistablePayload, ImmutableDaoStateModel, Canonical {
    private final Proposal proposal;
    private final long stakeOfAcceptedVotes;
    private final long stakeOfRejectedVotes;
    private final int numAcceptedVotes;
    private final int numRejectedVotes;
    private final int numIgnoredVotes;

    public ProposalVoteResult(Proposal proposal, long stakeOfAcceptedVotes, long stakeOfRejectedVotes,
                              int numAcceptedVotes, int numRejectedVotes, int numIgnoredVotes) {
        this.proposal = proposal;
        this.stakeOfAcceptedVotes = stakeOfAcceptedVotes;
        this.stakeOfRejectedVotes = stakeOfRejectedVotes;
        this.numAcceptedVotes = numAcceptedVotes;
        this.numRejectedVotes = numRejectedVotes;
        this.numIgnoredVotes = numIgnoredVotes;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.ProposalVoteResult toProtoMessage() {
        protobuf.ProposalVoteResult.Builder builder = protobuf.ProposalVoteResult.newBuilder()
                .setProposal(proposal.toProtoMessage())
                .setStakeOfAcceptedVotes(stakeOfAcceptedVotes)
                .setStakeOfRejectedVotes(stakeOfRejectedVotes)
                .setNumAcceptedVotes(numAcceptedVotes)
                .setNumRejectedVotes(numRejectedVotes)
                .setNumIgnoredVotes(numIgnoredVotes);
        return builder.build();
    }

    public static ProposalVoteResult fromProto(protobuf.ProposalVoteResult proto) {
        return new ProposalVoteResult(Proposal.fromProto(proto.getProposal()),
                proto.getStakeOfAcceptedVotes(),
                proto.getStakeOfRejectedVotes(),
                proto.getNumAcceptedVotes(),
                proto.getNumRejectedVotes(),
                proto.getNumIgnoredVotes());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final CanonicalSchema<ProposalVoteResult> SCHEMA =
            CanonicalSchema.<ProposalVoteResult>newBuilder("ProposalVoteResult")
                    .compose(1, "proposal", ProposalVoteResult::getProposal, Proposal.SCHEMA)
                    .int64(2, "stake_of_Accepted_votes", ProposalVoteResult::getStakeOfAcceptedVotes)
                    .int64(3, "stake_of_Rejected_votes", ProposalVoteResult::getStakeOfRejectedVotes)
                    .int32(4, "num_accepted_votes", ProposalVoteResult::getNumAcceptedVotes)
                    .int32(5, "num_rejected_votes", ProposalVoteResult::getNumRejectedVotes)
                    .int32(6, "num_ignored_votes", ProposalVoteResult::getNumIgnoredVotes)
                    .build();

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(this, SCHEMA);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getNumActiveVotes() {
        return numAcceptedVotes + numRejectedVotes;
    }

    public long getQuorum() {
        // Quorum is sum of all votes independent if accepted or rejected.
        log.debug("Quorum: proposalTxId: {}, totalStake: {}, stakeOfAcceptedVotes: {}, stakeOfRejectedVotes: {}",
                proposal.getTxId(), getTotalStake(), stakeOfAcceptedVotes, stakeOfRejectedVotes);
        return getTotalStake();
    }

    public long getThreshold() {
        checkArgument(stakeOfAcceptedVotes >= 0, "stakeOfAcceptedVotes must not be negative");
        checkArgument(stakeOfRejectedVotes >= 0, "stakeOfRejectedVotes must not be negative");
        if (stakeOfAcceptedVotes == 0) {
            return 0;
        }
        return stakeOfAcceptedVotes * 10_000 / getTotalStake();
    }

    @Override
    public String toString() {
        return "ProposalVoteResult{" +
                "\n     proposal=" + proposal +
                ",\n     stakeOfAcceptedVotes=" + stakeOfAcceptedVotes +
                ",\n     stakeOfRejectedVotes=" + stakeOfRejectedVotes +
                ",\n     numAcceptedVotes=" + numAcceptedVotes +
                ",\n     numRejectedVotes=" + numRejectedVotes +
                ",\n     numIgnoredVotes=" + numIgnoredVotes +
                "\n}";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private long getTotalStake() {
        return stakeOfAcceptedVotes + stakeOfRejectedVotes;
    }
}
