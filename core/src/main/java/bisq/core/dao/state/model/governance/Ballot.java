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

package bisq.core.dao.state.model.governance;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.persistable.PersistablePayload;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Base class for all ballots like compensation request, generic request, remove asset ballots and
 * change param ballots.
 * It contains the Proposal and the Vote. If a Proposal is ignored for voting the vote object is null.
 *
 * One proposal has about 278 bytes
 */
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
public final class Ballot implements PersistablePayload, ConsensusCritical, ImmutableDaoStateModel {
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

    public Ballot(Proposal proposal, @Nullable Vote vote) {
        this.proposal = proposal;
        this.vote = vote;
    }

    @Override
    public protobuf.Ballot toProtoMessage() {
        final protobuf.Ballot.Builder builder = protobuf.Ballot.newBuilder()
                .setProposal(proposal.getProposalBuilder());
        Optional.ofNullable(vote).ifPresent(e -> builder.setVote((protobuf.Vote) e.toProtoMessage()));
        return builder.build();
    }

    public static Ballot fromProto(protobuf.Ballot proto) {
        return new Ballot(Proposal.fromProto(proto.getProposal()),
                proto.hasVote() ? Vote.fromProto(proto.getVote()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setVote(@Nullable Vote vote) {
        this.vote = vote;
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

    public String info() {
        return "Ballot{" +
                "\n     proposalTxId=" + proposal.getTxId() +
                ",\n     vote=" + vote +
                "\n}";
    }
}
