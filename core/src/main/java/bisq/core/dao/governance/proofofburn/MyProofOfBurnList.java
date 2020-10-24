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

package bisq.core.dao.governance.proofofburn;

import bisq.common.proto.persistable.PersistableList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of MyProofOfBurn objects.
 */
@EqualsAndHashCode(callSuper = true)
public class MyProofOfBurnList extends PersistableList<MyProofOfBurn> {

    private MyProofOfBurnList(List<MyProofOfBurn> list) {
        super(list);
    }

    MyProofOfBurnList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.PersistableEnvelope toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder().setMyProofOfBurnList(getBuilder()).build();
    }

    private protobuf.MyProofOfBurnList.Builder getBuilder() {
        return protobuf.MyProofOfBurnList.newBuilder()
                .addAllMyProofOfBurn(getList().stream()
                        .map(MyProofOfBurn::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static MyProofOfBurnList fromProto(protobuf.MyProofOfBurnList proto) {
        return new MyProofOfBurnList(new ArrayList<>(proto.getMyProofOfBurnList().stream()
                .map(MyProofOfBurn::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of txIds in MyProofOfBurnList: " + getList().stream()
                .map(MyProofOfBurn::getTxId)
                .collect(Collectors.toList());
    }
}
