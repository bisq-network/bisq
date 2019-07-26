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

package bisq.core.dao.monitoring.network.messages;

import bisq.core.dao.monitoring.model.DaoStateHash;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class NewDaoStateHashMessage extends NewStateHashMessage<DaoStateHash> {
    public NewDaoStateHashMessage(DaoStateHash daoStateHash) {
        super(daoStateHash, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private NewDaoStateHashMessage(DaoStateHash daoStateHash, int messageVersion) {
        super(daoStateHash, messageVersion);
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setNewDaoStateHashMessage(protobuf.NewDaoStateHashMessage.newBuilder()
                        .setStateHash(stateHash.toProtoMessage()))
                .build();
    }

    public static NetworkEnvelope fromProto(protobuf.NewDaoStateHashMessage proto, int messageVersion) {
        return new NewDaoStateHashMessage(DaoStateHash.fromProto(proto.getStateHash()), messageVersion);
    }

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.DAO_STATE);
    }
}
