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

public class GenesisTest extends AbstractAssetTest {

    public GenesisTest() {
        super(new Genesis());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("STE5agX1VkUKZRTHBFufkQu6JtNP1QYJcd"); // Standard SegWit
        assertValidAddress("SNMcFfcFkes6bWR5dviWQQAL4SYQg8T4Vu"); // Standard SegWit
        assertValidAddress("SfMmJJdg8uDHK6ajurBNksry7zu3KHdbPv"); // Standard SegWit
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("genx1q5dlyjsktuztnwzs85as7vslqfddcmenhfc0ehl"); // Bech32
        assertInvalidAddress("genx1qxc0hp76tx9hse2evt8dx2k686nx42ljel5nenr"); // Bech32
        assertInvalidAddress("CT747k1CThgCxk4xRPQeJP6pyKiTfzRM1R"); // valid but unsupported legacy
        assertInvalidAddress("CbGwkAWfLXjU2esjomFzJfKAFdUiKQjJUd"); // valid but unsupported legacy
        assertInvalidAddress("0213ba949e295aabda252662ffed7c4c0906"); // random garbage
        assertInvalidAddress("BwyzAAjVwV2mhR2WQ8SfXhHyUDoy4VL16zBc"); // random garbage
        assertInvalidAddress("EpGQR83U34JRszcGENjegZLCoDLTwG6YWLBN7jVC"); // random garbage
        assertInvalidAddress("Xp3Gv2JiP487Z8SULctveCKNM1ffpz5b3n"); // random garbage
    }
}
