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

public class BitCloudTest extends AbstractAssetTest {

    public BitCloudTest() {
        super(new BitCloud());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("B7dtuCkeYcQQiTdr9wAK7HZtmvvrM24jEH");
        assertValidAddress("BGp8DmF5wwpaDUsK9Mi3SByTDuvgy6XJGS");
        assertValidAddress("BDL6jpyNge8VB8LL8dEDX1bxfZ3jHGYzS3");
        assertValidAddress("BKuvEvMrXmPFPC3YGGHHnZoTVYxeddxAjC");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#");
    }
}
