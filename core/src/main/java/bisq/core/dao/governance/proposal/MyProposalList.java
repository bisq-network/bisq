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

package bisq.core.dao.governance.proposal;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of proposals. Used in vote consensus, so changes can break consensus!
 */
@EqualsAndHashCode(callSuper = true)
public class MyProposalList extends PersistableList<Proposal> implements ConsensusCritical {

    public MyProposalList(List<Proposal> list) {
        super(list);
    }

    MyProposalList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setMyProposalList(getBuilder()).build();
    }

    private PB.MyProposalList.Builder getBuilder() {
        return PB.MyProposalList.newBuilder()
                .addAllProposal(getList().stream()
                        .map(Proposal::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static MyProposalList fromProto(PB.MyProposalList proto) {
        return new MyProposalList(new ArrayList<>(proto.getProposalList().stream()
                .map(Proposal::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of TxId's in MyProposalList: " + getList().stream()
                .map(Proposal::getTxId)
                .collect(Collectors.toList());
    }
}

