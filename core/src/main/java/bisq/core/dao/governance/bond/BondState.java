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

/**
 * Holds the different states of a bond.
 * Used also in string properties ("dao.bond.bondState.*")
 */
public enum BondState {
    UNDEFINED,
    READY_FOR_LOCKUP,       // Accepted by voting (if role) but no lockup tx made yet.
    LOCKUP_TX_PENDING,      // Tx broadcasted but not confirmed. Used only by tx publisher.
    LOCKUP_TX_CONFIRMED,
    UNLOCK_TX_PENDING,      // Tx broadcasted but not confirmed. Used only by tx publisher.
    UNLOCK_TX_CONFIRMED,
    UNLOCKING,              // Lock time still not expired
    UNLOCKED,               // Fully unlocked
    CONFISCATED;            // Bond got confiscated by DAO voting

    public boolean isActive() {
        return this == BondState.LOCKUP_TX_CONFIRMED ||
                this == BondState.UNLOCK_TX_PENDING ||
                this == BondState.UNLOCK_TX_CONFIRMED ||
                this == BondState.UNLOCKING;
    }
}
