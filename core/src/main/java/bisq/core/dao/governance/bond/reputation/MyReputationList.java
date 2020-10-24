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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of MyReputations.
 */
@EqualsAndHashCode(callSuper = true)
public class MyReputationList extends PersistableList<MyReputation> {

    private MyReputationList(List<MyReputation> list) {
        super(list);
    }

    MyReputationList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.PersistableEnvelope toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder().setMyReputationList(getBuilder()).build();
    }

    private protobuf.MyReputationList.Builder getBuilder() {
        return protobuf.MyReputationList.newBuilder()
                .addAllMyReputation(getList().stream()
                        .map(MyReputation::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static MyReputationList fromProto(protobuf.MyReputationList proto) {
        return new MyReputationList(new ArrayList<>(proto.getMyReputationList().stream()
                .map(MyReputation::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of salts in MyReputationList: " + getList().stream()
                .map(MyReputation::getSalt)
                .collect(Collectors.toList());
    }
}
