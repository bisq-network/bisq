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

package bisq.core.dao.governance.bond;

import java.util.Objects;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

/**
 * Base class for BondedRole and BondedReputation. Holds the state of the bonded asset.
 */
@Getter
public abstract class Bond<T extends BondedAsset> {
    @Getter
    protected final T bondedAsset;
    @Setter
    @Nullable
    protected String lockupTxId;
    @Setter
    @Nullable
    protected String unlockTxId;
    @Setter
    protected BondState bondState = BondState.READY_FOR_LOCKUP;
    @Setter
    private long amount;
    @Setter
    private long lockupDate;
    @Setter
    private long unlockDate;
    @Setter
    private int lockTime;

    protected Bond(T bondedAsset) {
        this.bondedAsset = bondedAsset;
    }

    public boolean isActive() {
        return bondState.isActive();
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bond)) return false;
        Bond<?> bond = (Bond<?>) o;
        return amount == bond.amount &&
                lockupDate == bond.lockupDate &&
                unlockDate == bond.unlockDate &&
                lockTime == bond.lockTime &&
                Objects.equals(bondedAsset, bond.bondedAsset) &&
                Objects.equals(lockupTxId, bond.lockupTxId) &&
                Objects.equals(unlockTxId, bond.unlockTxId) &&
                bondState.name().equals(bond.bondState.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(bondedAsset, lockupTxId, unlockTxId, bondState.name(), amount, lockupDate, unlockDate, lockTime);
    }

    @Override
    public String toString() {
        return "Bond{" +
                "\n     bondedAsset=" + bondedAsset +
                ",\n     lockupTxId='" + lockupTxId + '\'' +
                ",\n     unlockTxId='" + unlockTxId + '\'' +
                ",\n     bondState=" + bondState +
                ",\n     amount=" + amount +
                ",\n     lockupDate=" + lockupDate +
                ",\n     unlockDate=" + unlockDate +
                ",\n     lockTime=" + lockTime +
                "\n}";
    }
}
