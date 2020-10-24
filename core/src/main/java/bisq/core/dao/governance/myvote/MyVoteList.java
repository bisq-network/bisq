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

package bisq.core.dao.governance.myvote;

import bisq.common.proto.persistable.PersistableList;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class MyVoteList extends PersistableList<MyVote> {

    MyVoteList() {
        super();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MyVoteList(List<MyVote> list) {
        super(list);
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setMyVoteList(protobuf.MyVoteList.newBuilder()
                        .addAllMyVote(getList().stream()
                                .map(MyVote::toProtoMessage)
                                .collect(Collectors.toList())))
                .build();
    }

    public static MyVoteList fromProto(protobuf.MyVoteList proto) {
        return new MyVoteList(new ArrayList<>(proto.getMyVoteList().stream()
                .map(MyVote::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of TxId's in MyVoteList: " + getList().stream()
                .map(MyVote::getBlindVoteTxId)
                .collect(Collectors.toList());
    }
}
