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

public class WorldMobileCoinTest extends AbstractAssetTest {

    public WorldMobileCoinTest() {
        super(new WorldMobileCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("wc1qke2es507uz0dcfx7eyvlfuemwys8xem48vp5rw");
        assertValidAddress("wc1qlwsfmqswjnnv20quv203lnksjrgsww3mjhd349");
        assertValidAddress("Wmpfed6ykt9YsFxhGri5KJvKc3r7BC1rVQ");
        assertValidAddress("WSSqzNJvc4X4xWW6WDyUk1oWEeLx45vyRh");
        assertValidAddress("XWJk3GEuNFEn3dGFCi7vTzVuydeGQA9Fnq");
        assertValidAddress("XG1Mc7XvvpR1wQvjeikZwHAjwLvCWQD35u");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("wc1qke2es507uz0dcfx7eyvlfuemwys8xem48vp5rx");
        assertInvalidAddress("Wmpfed6ykt9YsFxhGri5KJvKc3r7BC1rvq");
        assertInvalidAddress("XWJk3GEuNFEn3dGFCi7vTzVuydeGQA9FNQ");
        assertInvalidAddress("0123456789Abcdefghijklmnopqrstuvwxyz");
    }
}
