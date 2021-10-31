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

package bisq.core.dao.state.storage;

import bisq.core.dao.monitoring.model.DaoStateHash;

import bisq.common.proto.persistable.PersistableEnvelope;

import com.google.protobuf.Message;

import java.util.LinkedList;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;


@Slf4j
public class DaoStateStore implements PersistableEnvelope {
    @Getter
    @Setter
    @Nullable
    private protobuf.DaoState daoStateAsProto;

    @Getter
    @Setter
    private LinkedList<DaoStateHash> daoStateHashChain;

    DaoStateStore(@Nullable protobuf.DaoState daoStateAsProto, LinkedList<DaoStateHash> daoStateHashChain) {
        this.daoStateAsProto = daoStateAsProto;
        this.daoStateHashChain = daoStateHashChain;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Message toProtoMessage() {
        checkNotNull(daoStateAsProto, "daoStateAsProto must not be null when toProtoMessage is invoked");
        protobuf.DaoStateStore.Builder builder = protobuf.DaoStateStore.newBuilder()
                .setDaoState(daoStateAsProto)
                .addAllDaoStateHash(daoStateHashChain.stream()
                        .map(DaoStateHash::toProtoMessage)
                        .collect(Collectors.toList()));
        return protobuf.PersistableEnvelope.newBuilder()
                .setDaoStateStore(builder)
                .build();
    }

    public static DaoStateStore fromProto(protobuf.DaoStateStore proto) {
        LinkedList<DaoStateHash> daoStateHashList = proto.getDaoStateHashList().isEmpty() ?
                new LinkedList<>() :
                new LinkedList<>(proto.getDaoStateHashList().stream()
                        .map(DaoStateHash::fromProto)
                        .collect(Collectors.toList()));
        return new DaoStateStore(proto.getDaoState(), daoStateHashList);
    }

    public void releaseMemory() {
        daoStateAsProto = null;
        daoStateHashChain = null;
    }
}
