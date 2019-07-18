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

public class DonuTest extends AbstractAssetTest {

    public DonuTest() {
        super(new Donu());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("NS5cGWdERahJ11pn12GoV5Jb7nsLzdr3kP");
        assertValidAddress("NU7nCzyQiAtTxzXLnDsJu4NhwQqrnPyJZj");
        assertValidAddress("NeeAy35aQirpmTARHEXpP8uTmpPCcSD9Qn");
        assertValidAddress("NScgetCW5bqDTVWFH3EYNMtTo5RcvDxD6B");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("NScgetCW5bqDTVWFH3EYNMtTo5Rc#DxD6B");
        assertInvalidAddress("NeeAy35a0irpmTARHEXpP8uTmpPCcSD9Qn");
        assertInvalidAddress("neeAy35aQirpmTARHEXpP8uTmpPCcSD9Qn");
        assertInvalidAddress("NScgetCWRcvDxD6B");
    }
}
