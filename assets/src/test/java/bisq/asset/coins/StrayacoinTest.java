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

public class StrayacoinTest extends AbstractAssetTest {

    public StrayacoinTest() {
        super(new Strayacoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("SZHa3vS9ctDJwx3BziaqgN3zQMkYpgyP7f");
        assertValidAddress("SefAdKgyqdg7wd1emhFynPs44d1b2Ca2U1");
        assertValidAddress("SSw6555umxHsPZgE96KoyiEVY3CDuJRBQc");
        assertValidAddress("SYwJ6aXQkmt3ExuaXBSCmyiHRn8fUpxXUi");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("0x2a65Aca4D5fC5B5C859090a6c34d1641353982266");
        assertInvalidAddress("DNkkfdUvkCDiywYE98MTVp9nQJTgeZAiFr");
    }
}
