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


import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import java.util.Arrays;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Contains the blockHeight, the hash and the previous hash of the state.
 * As the hash is created from the state at the particular height including the previous hash we get the history of
 * the full chain included and we know if the hash matches at a particular height that all the past blocks need to match
 * as well.
 */
@EqualsAndHashCode
@Getter
@Slf4j
public abstract class StateHash implements PersistablePayload, NetworkPayload {
    protected final int height;
    protected final byte[] hash;

    StateHash(int height, byte[] hash) {
        this.height = height;
        this.hash = hash;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasEqualHash(StateHash other) {
        return Arrays.equals(hash, other.getHash());
    }

    public byte[] getHash() {
        return hash;
    }

    @Override
    public String toString() {
        return "StateHash{" +
                "\n     height=" + height +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}
