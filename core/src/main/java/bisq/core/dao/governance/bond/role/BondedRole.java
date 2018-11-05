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

package bisq.core.dao.governance.bond.role;

import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.state.model.governance.Role;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Wrapper for role which contains the mutable state of a bonded role. Only kept in memory.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class BondedRole extends Bond<Role> {

    BondedRole(Role role) {
        super(role);
    }

    @Override
    public String toString() {
        return "BondedRole{" +
                "\n} " + super.toString();
    }
}
