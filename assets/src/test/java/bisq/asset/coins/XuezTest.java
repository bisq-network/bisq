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

public class XuezTest extends AbstractAssetTest {

    public XuezTest() {
        super(new Xuez());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("XR2vhweAd6iyJcE7bTc89JdthXL7rLwjUt");
        assertValidAddress("XH1p4v6rzVWvWBCwYeVn5CEyPYrrYVWTih");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("tH1p4v6rzVWvWBCwYeVn5CEyPYrrYVWTih");
        assertInvalidAddress("38NwrYsD1HxQW5zfLT0QcUUXGMPvQgzTSn");
        assertInvalidAddress("0123456789");
        assertInvalidAddress("6H1p4v6rzVWvWBCwYeVn");
    }
}
