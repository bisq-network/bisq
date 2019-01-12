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

public class UnitedCommunityCoinTest extends AbstractAssetTest {

    public UnitedCommunityCoinTest() {
        super(new UnitedCommunityCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("UX3DVuoiNR9Uwa22NLehu8yVKecjFKn4ii");
        assertValidAddress("URqRRRFY7D6drJCput5UjTRUQYEL8npUwk");
        assertValidAddress("Uha1WUkuYtW9Uapme2E46PBz2sBkM9qV9w");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("UX3DVuoiNR90wa22NLehu8yVKecjFKn4ii");
        assertInvalidAddress("URqRRRFY7D6drJCput5UjTRUQYaEL8npUwk");
        assertInvalidAddress("Uha1WUkuYtW9Uapme2E46PBz2$BkM9qV9w");
    }
}
