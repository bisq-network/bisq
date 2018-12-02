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

public class PZDCTest extends AbstractAssetTest {

    public PZDCTest() {
        super(new PZDC());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("PNxERPUbkvCYeuJk44pH8bsdQJenvEWt5J");
        assertValidAddress("PCwCT1PkW2RsxT8jTb21vRnNDQGDRcWNkM");
        assertValidAddress("PPD3mYyS3vsHBkCrbCfrZyrwCGdr6EJHgG");
        assertValidAddress("PTQDhqksrocR7Z516zbpjuXSGVD37iu8gy");
        assertValidAddress("PXtABooQW1ED9NkARTiFcZv6xUnMmrbhpt");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("pGXsg0jSMzh1dSqggRvHjPvE3cnwvuXC7s");
        assertInvalidAddress("PKfRRcjwzKFq3dIqE9gq8Ztxn922W4GZhm");
        assertInvalidAddress("PKfRRcjwzKFq3d0qE9gq8Ztxn922W4GZhm");
        assertInvalidAddress("PKfRRcjwzKFq3dOqE9gq8Ztxn922W4GZhm");
        assertInvalidAddress("PKfRRcjwzKFq3dlqE9gq8Ztxn922W4GZhm");
        assertInvalidAddress("PXP75NnwDryYswQb9RaPFBchqLRSvBmDP");
        assertInvalidAddress("PKr3vQ7S");
    }
}
