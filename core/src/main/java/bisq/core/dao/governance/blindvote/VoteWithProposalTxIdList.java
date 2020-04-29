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

import bisq.common.Proto;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * We encode the VoteWithProposalTxId list to PB bytes in the blindVote. The bytes get encrypted and later decrypted.
 * To use a ByteOutputStream and add all list elements would work for encryption but for decrypting we don't know the
 * length of a list entry and it would make the process complicated (e.g. require a custom serialisation format).
 */
@Slf4j
@Value
public class VoteWithProposalTxIdList implements Proto, ConsensusCritical {
    private final List<VoteWithProposalTxId> list;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static VoteWithProposalTxIdList getVoteWithProposalTxIdListFromBytes(byte[] bytes) throws InvalidProtocolBufferException {
        return VoteWithProposalTxIdList.fromProto(protobuf.VoteWithProposalTxIdList.parseFrom(bytes));
    }

    @Override
    public protobuf.VoteWithProposalTxIdList toProtoMessage() {
        return getBuilder().build();
    }

    private protobuf.VoteWithProposalTxIdList.Builder getBuilder() {
        return protobuf.VoteWithProposalTxIdList.newBuilder()
                .addAllItem(getList().stream()
                        .map(VoteWithProposalTxId::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    private static VoteWithProposalTxIdList fromProto(protobuf.VoteWithProposalTxIdList proto) {
        final ArrayList<VoteWithProposalTxId> list = proto.getItemList().stream()
                .map(VoteWithProposalTxId::fromProto).collect(Collectors.toCollection(ArrayList::new));
        return new VoteWithProposalTxIdList(list);
    }

    @Override
    public String toString() {
        return "VoteWithProposalTxIdList: " + getList().stream()
                .map(VoteWithProposalTxId::getProposalTxId)
                .collect(Collectors.toList());
    }
}
