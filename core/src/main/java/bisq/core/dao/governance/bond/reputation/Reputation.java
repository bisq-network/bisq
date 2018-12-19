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

package bisq.core.dao.governance.bond.reputation;

import bisq.core.dao.governance.bond.BondedAsset;

import bisq.common.util.Utilities;

import java.util.Arrays;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Reputation objects we found on the blockchain. We only know the hash of it.
 * In contrast to MyReputation which represents the object we created and contains the
 * private salt data.
 */
@Immutable
@Value
@Slf4j
public final class Reputation implements BondedAsset {
    private final byte[] hash;

    public Reputation(byte[] hash) {
        this.hash = hash;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BondedAsset implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public String getDisplayString() {
        return Utilities.bytesAsHexString(hash);
    }

    @Override
    public String getUid() {
        return Utilities.bytesAsHexString(hash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reputation)) return false;
        if (!super.equals(o)) return false;
        Reputation that = (Reputation) o;
        return Arrays.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }

    @Override
    public String toString() {
        return "Reputation{" +
                "\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}
