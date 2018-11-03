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

import bisq.core.locale.Res;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

/**
 * Wrapper for reputation which contains the mutable state of a bonded reputation. Only kept in memory.
 */
@Getter
public final class BondedReputation {
    private final Reputation reputation;
    @Nullable
    @Setter
    private String lockupTxId;
    @Nullable
    @Setter
    private String unlockTxId;

    public BondedReputation(Reputation reputation) {
        this.reputation = reputation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getDisplayString() {
        return Res.get("dao.bond.bondedReputation");
    }

    public boolean isLockedUp() {
        return lockupTxId != null;
    }

    public boolean isUnlocked() {
        return unlockTxId != null;
    }


    @Override
    public String toString() {
        return "BondedReputation{" +
                "\n     reputation='" + reputation + '\'' +
                ",\n     lockupTxId='" + lockupTxId + '\'' +
                ",\n     unlockTxId='" + unlockTxId + '\'' +
                "\n}";
    }
}
