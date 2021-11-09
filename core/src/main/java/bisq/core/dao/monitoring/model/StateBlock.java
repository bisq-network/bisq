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

package bisq.core.dao.monitoring.model;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Contains my StateHash at a particular block height and the received stateHash from our peers.
 * The maps get updated over time, this is not an immutable class.
 */
@Getter
@EqualsAndHashCode
public abstract class StateBlock<T extends StateHash> {
    protected final T myStateHash;

    private final Map<String, T> peersMap = new HashMap<>();
    private final Map<String, T> inConflictMap = new HashMap<>();

    StateBlock(T myStateHash) {
        this.myStateHash = myStateHash;
    }

    public void putInPeersMap(String peersNodeAddress, T stateHash) {
        peersMap.putIfAbsent(peersNodeAddress, stateHash);
    }

    public void putInConflictMap(String peersNodeAddress, T stateHash) {
        inConflictMap.putIfAbsent(peersNodeAddress, stateHash);
    }

    // Delegates
    public int getHeight() {
        return myStateHash.getHeight();
    }

    public byte[] getHash() {
        return myStateHash.getHash();
    }

    @Override
    public String toString() {
        return "StateBlock{" +
                "\n     myStateHash=" + myStateHash +
                ",\n     peersMap=" + peersMap +
                ",\n     inConflictMap=" + inConflictMap +
                "\n}";
    }
}
