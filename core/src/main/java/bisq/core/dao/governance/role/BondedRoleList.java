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

package bisq.core.dao.governance.role;

import bisq.core.dao.governance.ConsensusCritical;

import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of bondedRoles.
 */
@EqualsAndHashCode(callSuper = true)
public class BondedRoleList extends PersistableList<BondedRole> implements ConsensusCritical {

    public BondedRoleList(List<BondedRole> list) {
        super(list);
    }

    public BondedRoleList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setBondedRoleList(getBuilder()).build();
    }

    public PB.BondedRoleList.Builder getBuilder() {
        return PB.BondedRoleList.newBuilder()
                .addAllBondedRole(getList().stream()
                        .map(BondedRole::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static BondedRoleList fromProto(PB.BondedRoleList proto) {
        return new BondedRoleList(new ArrayList<>(proto.getBondedRoleList().stream()
                .map(BondedRole::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of lockupTxIds in BondedRoleList: " + getList().stream()
                .map(BondedRole::getLockupTxId)
                .collect(Collectors.toList());
    }
}

