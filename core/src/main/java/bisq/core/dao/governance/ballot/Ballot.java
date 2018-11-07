/*
 * This file is part of Bisq.
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

package bisq.core.dao.governance.ballot;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.governance.ballot.vote.Vote;
import bisq.core.dao.governance.proposal.Proposal;
import bisq.core.dao.governance.proposal.ProposalType;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Base class for all ballots like compensation request, generic request, remove asset ballots and
 * change param ballots.
 * It contains the Proposal and the Vote. If a Proposal is ignored for voting the vote object is null.
 *
 * One proposal has about 278 bytes
 */
@Slf4j
@Getter
@EqualsAndHashCode
public final class Ballot implements PersistablePayload, ConsensusCritical {
    protected final Proposal proposal;

    @Nullable
    protected Vote vote;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Ballot(Proposal proposal) {
        this(proposal, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Ballot(Proposal proposal,
                  @Nullable Vote vote) {
        this.proposal = proposal;
        this.vote = vote;
    }

    @Override
    public PB.Ballot toProtoMessage() {
        final PB.Ballot.Builder builder = PB.Ballot.newBuilder()
                .setProposal(proposal.getProposalBuilder());
        Optional.ofNullable(vote).ifPresent(e -> builder.setVote((PB.Vote) e.toProtoMessage()));
        return builder.build();
    }

    public static Ballot fromProto(PB.Ballot proto) {
        return new Ballot(Proposal.fromProto(proto.getProposal()),
                proto.hasVote() ? Vote.fromProto(proto.getVote()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setVote(@Nullable Vote vote) {
        this.vote = vote;
    }

    public ProposalType getType() {
        return getProposal().getType();
    }

    public String getTxId() {
        return proposal.getTxId();
    }

    public Optional<Vote> getVoteAsOptional() {
        return Optional.ofNullable(vote);
    }

    @Override
    public String toString() {
        return "Ballot{" +
                "\n     proposal=" + proposal +
                ",\n     vote=" + vote +
                "\n}";
    }
}
