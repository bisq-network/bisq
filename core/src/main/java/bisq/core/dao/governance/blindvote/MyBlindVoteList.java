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

package bisq.core.dao.governance.blindvote;

import bisq.core.dao.governance.ConsensusCritical;

import bisq.common.proto.persistable.PersistableList;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * List of my own blind votes. Blind votes received from other voters are stored in the BlindVoteStore.
 */
@EqualsAndHashCode(callSuper = true)
public class MyBlindVoteList extends PersistableList<BlindVote> implements ConsensusCritical {

    MyBlindVoteList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MyBlindVoteList(List<BlindVote> list) {
        super(list);
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setMyBlindVoteList(protobuf.MyBlindVoteList.newBuilder()
                        .addAllBlindVote(getList().stream()
                                .map(BlindVote::toProtoMessage)
                                .collect(Collectors.toList())))
                .build();
    }

    public static MyBlindVoteList fromProto(protobuf.MyBlindVoteList proto) {
        return new MyBlindVoteList(new ArrayList<>(proto.getBlindVoteList().stream()
                .map(BlindVote::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "MyBlindVoteList: " + getList().stream()
                .map(BlindVote::getTxId)
                .collect(Collectors.toList());
    }
}
