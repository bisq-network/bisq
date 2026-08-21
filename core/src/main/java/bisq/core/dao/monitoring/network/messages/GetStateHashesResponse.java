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

import bisq.core.dao.monitoring.model.StateHash;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.ExtendedDataSizePermission;
import bisq.network.p2p.InitialDataResponse;

import bisq.common.proto.network.NetworkEnvelope;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public abstract class GetStateHashesResponse<T extends StateHash> extends NetworkEnvelope implements DirectMessage,
        ExtendedDataSizePermission, InitialDataResponse {
    protected final List<T> stateHashes;
    protected final int requestNonce;

    protected GetStateHashesResponse(List<T> stateHashes,
                                     int requestNonce,
                                     int messageVersion) {
        super(messageVersion);
        this.stateHashes = stateHashes;
        this.requestNonce = requestNonce;
    }

    @Override
    public String toString() {
        return "GetStateHashesResponse{" +
                "\n     stateHashes=" + stateHashes +
                ",\n     requestNonce=" + requestNonce +
                "\n} " + super.toString();
    }
}
