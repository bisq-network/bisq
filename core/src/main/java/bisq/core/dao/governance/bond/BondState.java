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

// Used in string properties ("dao.bond.bondState.*")
public enum BondState {
    READY_FOR_LOCKUP,       // Accepted by voting but no lockup tx made yet.
    LOCKUP_TX_PENDING,      // Tx broadcasted but not confirmed. Used only by tx publisher.
    LOCKUP_TX_CONFIRMED,
    UNLOCK_TX_PENDING,      // Tx broadcasted but not confirmed. Used only by tx publisher.
    UNLOCK_TX_CONFIRMED,
    UNLOCKING,              // lock time not expired
    UNLOCKED,
    CONFISCATED
}
