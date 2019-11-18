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

package bisq.core.support.dispute.refund;

import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;

import bisq.common.proto.ProtoUtil;
import bisq.common.storage.Storage;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@ToString
/*
 * Holds a List of refund dispute objects.
 *
 * Calls to the List are delegated because this class intercepts the add/remove calls so changes
 * can be saved to disc.
 */
public final class RefundDisputeList extends DisputeList<RefundDisputeList> {

    RefundDisputeList(Storage<RefundDisputeList> storage) {
        super(storage);
    }

    @Override
    public void readPersisted() {
        // We need to use DisputeList as file name to not lose existing disputes which are stored in the DisputeList file
        RefundDisputeList persisted = storage.initAndGetPersisted(this, "RefundDisputeList", 50);
        if (persisted != null) {
            list.addAll(persisted.getList());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RefundDisputeList(Storage<RefundDisputeList> storage, List<Dispute> list) {
        super(storage, list);
    }

    @Override
    public Message toProtoMessage() {

        list.forEach(dispute -> checkArgument(dispute.getSupportType().equals(SupportType.REFUND), "Support type has to be REFUND"));

        return protobuf.PersistableEnvelope.newBuilder().setRefundDisputeList(protobuf.RefundDisputeList.newBuilder()
                .addAllDispute(ProtoUtil.collectionToProto(new ArrayList<>(list), protobuf.Dispute.class))).build();
    }

    public static RefundDisputeList fromProto(protobuf.RefundDisputeList proto,
                                              CoreProtoResolver coreProtoResolver,
                                              Storage<RefundDisputeList> storage) {
        List<Dispute> list = proto.getDisputeList().stream()
                .map(disputeProto -> Dispute.fromProto(disputeProto, coreProtoResolver))
                .collect(Collectors.toList());

        list.forEach(e -> {
            checkArgument(e.getSupportType().equals(SupportType.REFUND), "Support type has to be REFUND");
            e.setStorage(storage);
        });
        return new RefundDisputeList(storage, list);
    }
}
