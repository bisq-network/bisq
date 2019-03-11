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

package bisq.core.dao.state.monitoring;

import bisq.common.util.Utilities;

import java.util.List;

import lombok.Value;

/**
 * Contains the dao state of at a particular block height we have received from our peers.
 */
@Value
public class NetworkDaoState {
    private final int height;
    private final List<PeersDaoStateHash> misMatchList;
    private final int numNetworkMessages;
    private final byte[] myHash;

    NetworkDaoState(int height, List<PeersDaoStateHash> misMatchList, int numNetworkMessages, byte[] myHash) {
        this.height = height;
        this.misMatchList = misMatchList;
        this.numNetworkMessages = numNetworkMessages;
        this.myHash = myHash;
    }

    @Override
    public String toString() {
        return "NetworkDaoState{" +
                "\n     height=" + height +
                ",\n     misMatchList=" + misMatchList +
                ",\n     numNetworkMessages=" + numNetworkMessages +
                ",\n     myHash=" + Utilities.bytesAsHexString(myHash) +
                "\n}";
    }
}
