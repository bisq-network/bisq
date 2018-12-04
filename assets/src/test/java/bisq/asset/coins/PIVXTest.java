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

public class PIVXTest extends AbstractAssetTest {

    public PIVXTest() {
        super(new PIVX());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("DFJku78A14HYwPSzC5PtUmda7jMr5pbD2B");
        assertValidAddress("DAeiBSH4nudXgoxS4kY6uhTPobc7ALrWDA");
        assertValidAddress("DRbnCYbuMXdKU4y8dya9EnocL47gFjErWe");
        assertValidAddress("DTPAqTryNRCE2FgsxzohTtJXfCBCDnG6Rc");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("dFJku78A14HYwPSzC5PtUmda7jMr5pbD2B");
        assertInvalidAddress("DAeiBSH4nudXgoxS4kY6uhTPobc7AlrWDA");
        assertInvalidAddress("DRbnCYbuMXdKU4y8dya9EnocL47gFjErWeg");
        assertInvalidAddress("DTPAqTryNRCE2FgsxzohTtJXfCBODnG6Rc");
        assertInvalidAddress("DTPAqTryNRCE2FgsxzohTtJXfCB0DnG6Rc");
        assertInvalidAddress("DTPAqTryNRCE2FgsxzohTtJXfCBIDnG6Rc");
    }
}
