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

import bisq.common.proto.persistable.PersistablePayload;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

@Immutable
@Value
public class EvaluatedProposal implements PersistablePayload, ImmutableDaoStateModel {
    private final boolean isAccepted;
    private final ProposalVoteResult proposalVoteResult;

    public EvaluatedProposal(boolean isAccepted, ProposalVoteResult proposalVoteResult) {
        this.isAccepted = isAccepted;
        this.proposalVoteResult = proposalVoteResult;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.EvaluatedProposal toProtoMessage() {
        protobuf.EvaluatedProposal.Builder builder = protobuf.EvaluatedProposal.newBuilder()
                .setIsAccepted(isAccepted)
                .setProposalVoteResult(proposalVoteResult.toProtoMessage());
        return builder.build();
    }

    public static EvaluatedProposal fromProto(protobuf.EvaluatedProposal proto) {
        return new EvaluatedProposal(proto.getIsAccepted(),
                ProposalVoteResult.fromProto(proto.getProposalVoteResult()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Proposal getProposal() {
        return proposalVoteResult.getProposal();
    }

    public String getProposalTxId() {
        return getProposal().getTxId();
    }

    @Override
    public String toString() {
        return "EvaluatedProposal{" +
                "\n     isAccepted=" + isAccepted +
                ",\n     proposalVoteResult=" + proposalVoteResult +
                "\n}";
    }
}
