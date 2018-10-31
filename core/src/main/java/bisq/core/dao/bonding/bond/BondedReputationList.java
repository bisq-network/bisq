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

package bisq.core.dao.bonding.bond;

import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of BondedReputations.
 */
@EqualsAndHashCode(callSuper = true)
public class BondedReputationList extends PersistableList<BondedReputation> {

    public BondedReputationList(List<BondedReputation> list) {
        super(list);
    }

    public BondedReputationList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setBondedReputationList(getBuilder()).build();
    }

    public PB.BondedReputationList.Builder getBuilder() {
        return PB.BondedReputationList.newBuilder()
                .addAllBondedReputation(getList().stream()
                        .map(BondedReputation::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static BondedReputationList fromProto(PB.BondedReputationList proto) {
        return new BondedReputationList(new ArrayList<>(proto.getBondedReputationList().stream()
                .map(BondedReputation::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of salts in BondedReputationList: " + getList().stream()
                .map(BondedReputation::getSalt)
                .collect(Collectors.toList());
    }
}

