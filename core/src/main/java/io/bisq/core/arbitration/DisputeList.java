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

package io.bisq.core.arbitration;

import com.google.protobuf.Message;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.storage.Storage;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.generated.protobuffer.PB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@ToString
/**
 * Holds a List of Dispute objects.
 *
 * Calls to the List are delegated because this class intercepts the add/remove calls so changes
 * can be saved to disc.
 */
public final class DisputeList implements PersistableEnvelope, PersistedDataHost {
    transient private final Storage<DisputeList> storage;
    @Getter
    private final ObservableList<Dispute> list = FXCollections.observableArrayList();

    public DisputeList(Storage<DisputeList> storage) {
        this.storage = storage;
    }

    @Override
    public void readPersisted() {
        DisputeList persisted = storage.initAndGetPersisted(this, 50);
        if (persisted != null)
            list.addAll(persisted.getList());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DisputeList(Storage<DisputeList> storage, List<Dispute> list) {
        this.storage = storage;
        this.list.addAll(list);
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setDisputeList(PB.DisputeList.newBuilder()
                .addAllDispute(ProtoUtil.collectionToProto(list))).build();
    }

    @Nullable
    public static DisputeList fromProto(PB.DisputeList proto,
                                        CoreProtoResolver coreProtoResolver,
                                        Storage<DisputeList> storage) {
        log.debug("DisputeList fromProto of {} ", proto);

        List<Dispute> list = proto.getDisputeList().stream()
                .map(disputeProto -> Dispute.fromProto(disputeProto, coreProtoResolver))
                .collect(Collectors.toList());
        list.stream().forEach(e -> e.setStorage(storage));
        return new DisputeList(storage, list);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean add(Dispute dispute) {
        if (!list.contains(dispute)) {
            boolean changed = list.add(dispute);
            if (changed)
                storage.queueUpForSave();
            return changed;
        } else {
            return false;
        }
    }

    public boolean remove(Object dispute) {
        //noinspection SuspiciousMethodCalls
        boolean changed = list.remove(dispute);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "SuspiciousMethodCalls"})
    public boolean contains(Object o) {
        return list.contains(o);
    }

    public Stream<Dispute> stream() {
        return list.stream();
    }
}
