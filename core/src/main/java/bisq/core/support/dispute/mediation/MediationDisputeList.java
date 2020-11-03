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

package bisq.core.support.dispute.mediation;

import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;

import bisq.common.proto.ProtoUtil;

import com.google.protobuf.Message;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
/*
 * Holds a List of mediation dispute objects.
 *
 * Calls to the List are delegated because this class intercepts the add/remove calls so changes
 * can be saved to disc.
 */
public final class MediationDisputeList extends DisputeList<Dispute> {

    MediationDisputeList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected MediationDisputeList(Collection<Dispute> collection) {
        super(collection);
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder().setMediationDisputeList(protobuf.MediationDisputeList.newBuilder()
                .addAllDispute(ProtoUtil.collectionToProto(getList(), protobuf.Dispute.class))).build();
    }

    public static MediationDisputeList fromProto(protobuf.MediationDisputeList proto,
                                                 CoreProtoResolver coreProtoResolver) {
        List<Dispute> list = proto.getDisputeList().stream()
                .map(disputeProto -> Dispute.fromProto(disputeProto, coreProtoResolver))
                .filter(e -> e.getSupportType().equals(SupportType.MEDIATION))
                .collect(Collectors.toList());
        return new MediationDisputeList(list);
    }
}
