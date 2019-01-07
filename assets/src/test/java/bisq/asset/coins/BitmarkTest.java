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

public class BitmarkTest extends AbstractAssetTest {

    public BitmarkTest() {
        super(new Bitmark());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("bMigVohTEiA3gxhFWpDJBrZ14j2RnDkWCs");
        assertValidAddress("bKMivcHXMNfs3P3AaTtyhDiZ7s8Nw3ek6L");
        assertValidAddress("bXUYGzbV8v6pLZtkYDL3feyrRFFnc37e3H");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("bMigVohTEiA3gxhFWpDJBrZ14j2RnDkWCt");
        assertInvalidAddress("F9z7PKmo1sLQYtFuZjTQ1zZXhPQtHLScKT");
        assertInvalidAddress("16Ftsh4Ff2ijPNsnQAUf5fKTp7DJaGxSZK");
    }
}
