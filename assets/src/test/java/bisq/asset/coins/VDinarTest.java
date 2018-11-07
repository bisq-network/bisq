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

public class VDinarTest extends AbstractAssetTest {

    public VDinarTest() {
        super(new VDinar());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("DG1KpSsSXd3uitgwHaA1i6T1Bj1hWEwAxB");
        assertValidAddress("DPEfTj1C9tTKEqkLPUwtUtCZHd7ViedBmZ");
        assertValidAddress("DLzjxv6Rk9hMYEFHBLqvyT8pkfS43u9Md5");
        assertValidAddress("DHexLqYt4ooDDnpmfEMSa1oJBbaZBxURZH");
        assertValidAddress("DHPybrRc2iqeE4aU8mmXKf8v38JTDyH2V9");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("3CDJNfdWX8m2NwuGUV3nhXHXEeLygMXoAj");
        assertInvalidAddress("DG1KpSsSXd3uitgwHaA1i6T1BjSHORTER");
        assertInvalidAddress("DG1KpSsSXd3uitgwHaA1i6T1Bj1hWLONGER");
        assertInvalidAddress("HG1KpSsSXd3uitgwHaA1i6T1Bj1hWEwAxB");
    }
}
