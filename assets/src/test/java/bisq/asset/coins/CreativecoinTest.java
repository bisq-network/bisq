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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class CreativecoinTest extends AbstractAssetTest {

    public CreativecoinTest() {
        super(new Creativecoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("CGjh99QdHxCE6g9pGUucCJNeUyQPRJr4fE");
        assertValidAddress("FTDYi4GoD3vFYFhEGbuifSZjs6udXVin7B");
        assertValidAddress("361uBxJvmg6f62dMYVM9b7GeR38phkkTyA");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("C7VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("F7VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#");
    }
}
