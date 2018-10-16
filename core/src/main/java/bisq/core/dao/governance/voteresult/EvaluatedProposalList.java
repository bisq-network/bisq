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

package bisq.core.dao.governance.voteresult;

import bisq.core.dao.governance.ConsensusCritical;

import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of evaluatedProposals.
 */
@EqualsAndHashCode(callSuper = true)
public class EvaluatedProposalList extends PersistableList<EvaluatedProposal> implements ConsensusCritical {

    public EvaluatedProposalList(List<EvaluatedProposal> list) {
        super(list);
    }

    public EvaluatedProposalList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setEvaluatedProposalList(getBuilder()).build();
    }

    public PB.EvaluatedProposalList.Builder getBuilder() {
        return PB.EvaluatedProposalList.newBuilder()
                .addAllEvaluatedProposal(getList().stream()
                        .map(EvaluatedProposal::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static EvaluatedProposalList fromProto(PB.EvaluatedProposalList proto) {
        return new EvaluatedProposalList(new ArrayList<>(proto.getEvaluatedProposalList().stream()
                .map(EvaluatedProposal::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of proposalTxId's in EvaluatedProposalList: " + getList().stream()
                .map(EvaluatedProposal::getProposalTxId)
                .collect(Collectors.toList());
    }
}

