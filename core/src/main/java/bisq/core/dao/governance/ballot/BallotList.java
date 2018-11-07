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

package bisq.core.dao.governance.ballot;

import bisq.core.dao.governance.ConsensusCritical;

import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of ballots.
 */
@EqualsAndHashCode(callSuper = true)
public class BallotList extends PersistableList<Ballot> implements ConsensusCritical {

    public BallotList(List<Ballot> list) {
        super(list);
    }

    BallotList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setBallotList(getBuilder()).build();
    }

    public PB.BallotList.Builder getBuilder() {
        return PB.BallotList.newBuilder()
                .addAllBallot(getList().stream()
                        .map(Ballot::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static BallotList fromProto(PB.BallotList proto) {
        return new BallotList(new ArrayList<>(proto.getBallotList().stream()
                .map(Ballot::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of UID's in BallotList: " + getList().stream()
                .map(Ballot::getTxId)
                .collect(Collectors.toList());
    }
}

