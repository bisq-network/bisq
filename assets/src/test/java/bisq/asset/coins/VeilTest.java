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

public class VeilTest extends AbstractAssetTest {

    public VeilTest() {
        super(new Veil());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("VS2oF2pouKoLPJCjY8D7E1dStmUtitACu7");
        assertValidAddress("VV8VtpWTsYFBnhnvgQVnTvqoTx7XRRevte");
        assertValidAddress("VRZF4Am891FS224uuNirsrEugqMyg3VxjJ");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX");
        assertInvalidAddress("DRbnCYbuMXdKU4y8dya9EnocL47gFjErWeg");
        assertInvalidAddress("DTPAqTryNRCE2FgsxzohTtJXfCBODnG6Rc");
        assertInvalidAddress("DTPAqTryNRCE2FgsxzohTtJXfCB0DnG6Rc");
        assertInvalidAddress("DTPAqTryNRCE2FgsxzohTtJXfCBIDnG6Rc");
    }
}
