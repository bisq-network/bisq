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

public class SpectrecoinTest extends AbstractAssetTest {

    public SpectrecoinTest() {
        super(new Spectrecoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("SUZRHjTLSCr581qLsGqMqBD5f3oW2JHckn");
        assertValidAddress("SZ4S1oFfUa4a9s9Kg8bNRywucHiDZmcUuz");
        assertValidAddress("SdyjGEmgroK2vxBhkHE1MBUVRbUWpRAdVG");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek");
    }
}
