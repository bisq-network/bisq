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

package bisq.core.dao.governance.bond.reputation;

import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of Reputations.
 */
@EqualsAndHashCode(callSuper = true)
public class ReputationList extends PersistableList<Reputation> {

    public ReputationList(List<Reputation> list) {
        super(list);
    }

    public ReputationList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setReputationList(getBuilder()).build();
    }

    public PB.ReputationList.Builder getBuilder() {
        return PB.ReputationList.newBuilder()
                .addAllReputation(getList().stream()
                        .map(Reputation::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static ReputationList fromProto(PB.ReputationList proto) {
        return new ReputationList(new ArrayList<>(proto.getReputationList().stream()
                .map(Reputation::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of salts in ReputationList: " + getList().stream()
                .map(Reputation::getSalt)
                .collect(Collectors.toList());
    }
}

