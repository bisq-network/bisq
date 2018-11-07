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

public class StelliteTest extends AbstractAssetTest {

    public StelliteTest() {
        super(new Stellite());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("Se3x7sVdvUnMMn2KoYLyYVHMJGRoB2R3V8K3LYuHAiEXgVac7vsmFiXUC8dSpJnjXDfwytKsQJV6HFH8MjwPagTJ2Aha46RZM");
        assertValidAddress("Se3F51UzpbVVnQRx2VNbcjfBoQJfeuyFF353i1jLnCZda9yVN3vy8csbYCESBvf38TFkchH1C1tMY6XHkC8L678K2vLsVZVMU");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("Se3x7svUnMMn2KoYLyYVHMJGRoB2R3V8K3LYuHAiEXgVac7vsmFiXUC8dSpJnjXDfwytKsQJV6HFH8MjwPagTJ2Aha46RZM");
        assertInvalidAddress("SX45GjRnvqheAgCpx4nJeKRjDtS5tYawxEP1GaTj79dTEm21Dtdxex6EHyDqBpofoDqW9k9uQWtkGgbbF8kiRSZ27AksBg7G111");
        assertInvalidAddress("Se3F51UzpbVVnQRx2VNbcjfBoQJfeuyFF353i1jLnCZda9yVN3vy8csbYCESBvf38TFkchH1C1tMY6XHkC8L678K2vLsVZVMUII");
    }
}
