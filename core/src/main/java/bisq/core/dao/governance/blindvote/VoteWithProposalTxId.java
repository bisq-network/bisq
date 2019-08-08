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

package bisq.core.dao.governance.blindvote;

import bisq.core.dao.state.model.governance.Vote;

import bisq.common.proto.persistable.PersistablePayload;

import java.util.Optional;

import lombok.Value;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;


@Value
public class VoteWithProposalTxId implements PersistablePayload {
    private final String proposalTxId;
    @Nullable
    private final Vote vote;

    VoteWithProposalTxId(String proposalTxId, @Nullable Vote vote) {
        this.proposalTxId = proposalTxId;
        this.vote = vote;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Used for sending over the network
    @Override
    public protobuf.VoteWithProposalTxId toProtoMessage() {
        return getBuilder().build();
    }

    @NotNull
    private protobuf.VoteWithProposalTxId.Builder getBuilder() {
        final protobuf.VoteWithProposalTxId.Builder builder = protobuf.VoteWithProposalTxId.newBuilder()
                .setProposalTxId(proposalTxId);
        Optional.ofNullable(vote).ifPresent(e -> builder.setVote((protobuf.Vote) e.toProtoMessage()));
        return builder;
    }

    public static VoteWithProposalTxId fromProto(protobuf.VoteWithProposalTxId proto) {
        return new VoteWithProposalTxId(proto.getProposalTxId(),
                proto.hasVote() ? Vote.fromProto(proto.getVote()) : null);
    }
}
