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

public class CloakCoinTest extends AbstractAssetTest {

    public CloakCoinTest() {
        super(new CloakCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("C3MwbThsvquwA4Yg6recThXpAhR2hvRKws");
        assertValidAddress("B6MwbThsvquwA4Yg6recThXpAhR2hvKRsz");
        assertValidAddress("BCA31xPpijxiCuTQeYMpMTQsTH1m2jTg5t");
	    assertValidAddress("smYmLVV33zExmaFyVp3AUjU3fJMK5E93kwzDfMnPLnEBQ7BoHZkSQhCP92hZz7Hm24yavCceNeQm8RHekqdvrhFe8gX7EdXNwnhQgQ");

    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1sA31xPpijxiCuTQeYMpMTQsTH1m2jTgtS");
        assertInvalidAddress("BsA31xPpijxiCuTQeYMpMTQsTH1m2jTgtSd");
        assertInvalidAddress("bech3ThsvquwA4Yg6recThXpAhR2hvRKws");
	    assertInvalidAddress("smYmLYcVVzExmaFyVp3AUjU3fJMK5E93kwzDfMnPLnEBQ7BoHZkSQhCP92hZz7Hm24yavCceNeQm8RHekqdv");
	    assertInvalidAddress("C3MwbThsvquwA4Yg6recThXpAhR2hvRKw");
	    assertInvalidAddress(" B6MwbThsvquwA4Yg6recThXpAhR2hvKRsz");
	    assertInvalidAddress("B6MwbThsvquwA4Yg6recThXpAhR2hvKRsz ");
    }
}
