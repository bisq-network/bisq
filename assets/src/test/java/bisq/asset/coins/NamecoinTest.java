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

import org.junit.Test;
import bisq.asset.AbstractAssetTest;

public class NamecoinTest extends AbstractAssetTest {

    public NamecoinTest() {
        super(new Namecoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("N7yhcPhzFduWXPc11AUK9zvtnsL6sgxmRs");
        assertValidAddress("N22FRU9f3fx7Hty641D5cg95kRK6S3sbf3");
        assertValidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQerY");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("N7yhcPhzFduWXPc11AUK9zvtnsL6sgxmRsx");
        assertInvalidAddress("MxmFPEPzF19JFPU3VPrRXvUbPjMQXnQer"); 
        assertInvalidAddress("bc1qus65zpte6qa2408qu3540lfcyj9cx7dphfcspn"); 
        assertInvalidAddress("3GyEtTwXhxbjBtmAR3CtzeayAyshtvd44P");
        assertInvalidAddress("1CnXYrivw7pJy3asKftp41wRPgBggF9fBw");
    }
}

