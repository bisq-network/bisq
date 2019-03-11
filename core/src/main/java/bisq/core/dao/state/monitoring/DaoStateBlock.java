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

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Delegate;

/**
 * Contains my DaoStateHash at a particular block height and the received daoStateHashes from our peers.
 * The maps get updated over time, this is not an immutable class.
 */
@Getter
@EqualsAndHashCode
public class DaoStateBlock {
    @Delegate
    private final DaoStateHash myDaoStateHash;

    private final Map<String, DaoStateHash> peersMap = new HashMap<>();
    private final Map<String, DaoStateHash> inConflictMap = new HashMap<>();

    DaoStateBlock(DaoStateHash myDaoStateHash) {
        this.myDaoStateHash = myDaoStateHash;
    }

    public void putInPeersMap(String peersNodeAddressAsString, DaoStateHash daoStateHash) {
        peersMap.putIfAbsent(peersNodeAddressAsString, daoStateHash);
    }

    public void putInConflictMap(String peersNodeAddressAsString, DaoStateHash daoStateHash) {
        inConflictMap.putIfAbsent(peersNodeAddressAsString, daoStateHash);
    }

    @Override
    public String toString() {
        return "DaoStateBlock{" +
                "\n     myDaoStateHash=" + myDaoStateHash +
                ",\n     peersMap=" + peersMap +
                ",\n     inConflictMap=" + inConflictMap +
                "\n}";
    }
}
