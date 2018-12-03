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

package bisq.core.dao.state;

import bisq.core.dao.state.model.DaoState;

import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;


@Slf4j
public class DaoStateStore implements PersistableEnvelope {
    // DaoState is always a clone and must not be used for read access beside initial read from disc when we apply
    // the snapshot!
    @Nullable
    @Getter
    @Setter
    DaoState daoState;

    DaoStateStore(DaoState daoState) {
        this.daoState = daoState;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Message toProtoMessage() {
        checkNotNull(daoState, "daoState must not be null when toProtoMessage is invoked");
        PB.DaoStateStore.Builder builder = PB.DaoStateStore.newBuilder()
                .setBsqState(daoState.getBsqStateBuilder());
        return PB.PersistableEnvelope.newBuilder()
                .setDaoStateStore(builder)
                .build();
    }

    public static PersistableEnvelope fromProto(PB.DaoStateStore proto) {
        return new DaoStateStore(DaoState.fromProto(proto.getBsqState()));
    }
}
