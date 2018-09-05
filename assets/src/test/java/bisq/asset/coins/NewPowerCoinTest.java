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

public class NewPowerCoinTest extends AbstractAssetTest {

    public NewPowerCoinTest() {
        super(new NewPowerCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("NXNc8LCAe2dHumQ9vTyogRXUzGw3PJHr55");
        assertValidAddress("NhWDeD4UaNK2Qj8oSKr9u7EAUkCFZxEsDr");
        assertValidAddress("NNTuHe4p5Xr8kyN2AJjJS9dcBoG1XQKkW6");
        assertValidAddress("NQebfM16pijp2KvFHTKQktD4y2cSKknQEg");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1111111111111111111111111111111111");
        assertInvalidAddress("2222222222222222222222222222222222");
        assertInvalidAddress("3333333333333333333333333333333333");
    }
}
