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

import bisq.core.dao.governance.bond.Bond;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Wrapper for reputation which contains the mutable state of my bonded reputation. Only kept in memory.
 * As it carries MyReputation it has access to the private salt data.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public final class MyBondedReputation extends Bond<MyReputation> {

    public MyBondedReputation(MyReputation myReputation) {
        super(myReputation);
    }

    @Override
    public String toString() {
        return "MyBondedReputation{" +
                "\n} " + super.toString();
    }
}
