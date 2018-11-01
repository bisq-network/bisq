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

package bisq.core.dao.governance.role;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

/**
 * Wrapper for role which contains the mutable state of a bonded role. Only kept in memory.
 */
@Getter
public class BondedRole {
    private final Role role;

    @Setter
    private long startDate;
    // LockupTxId is null as long the bond holder has not been accepted by voting and made the lockup tx.
    // It will get set after the proposal has been accepted and the lockup tx is confirmed.
    @Setter
    @Nullable
    private String lockupTxId;
    // Date when role has been revoked
    @Setter
    private long revokeDate;
    @Setter
    @Nullable
    private String unlockTxId;
    @Setter
    private boolean isUnlocking;

    BondedRole(Role role) {
        this.role = role;
    }

    public boolean isLockedUp() {
        return lockupTxId != null;
    }

    public boolean isUnlocked() {
        return unlockTxId != null;
    }
}
