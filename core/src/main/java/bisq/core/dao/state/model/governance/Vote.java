/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.state.model.governance;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

@Immutable
@Value
public class Vote implements PersistablePayload, NetworkPayload, ConsensusCritical, ImmutableDaoStateModel {
    private boolean accepted;

    public Vote(boolean accepted) {
        this.accepted = accepted;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        return PB.Vote.newBuilder()
                .setAccepted(accepted)
                .build();
    }

    public static Vote fromProto(PB.Vote proto) {
        return new Vote(proto.getAccepted());
    }
}
