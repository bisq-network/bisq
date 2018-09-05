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

public class ZeroOneCoinTest extends AbstractAssetTest {

    public ZeroOneCoinTest() {
        super(new ZeroOneCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("ZN17ww22Kg1cqM2VykoDwZW4fCTCvxGQMb");
        assertValidAddress("ZZJG1oqJ9VWHAy5AuE7bAugqoYvcGtPJcH");
        assertValidAddress("ZaUSzTWurWuaBw4zr8E4oEN25DzJK9vwbR");
        assertValidAddress("5AchYc7iQS7ynce7hNZ6Ya8djsbm5N9JBS");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("ZaUSzTWurWuaBw4zr8E4oEN25DzJK9vqqe");
        assertInvalidAddress("ZN17ww22Kg1cqM2VykoDwZW4fCTCvxGQM");
        assertInvalidAddress("ZaUSzTWurWuaBw4zr8E4oEN25DzJK9vwbb");
        assertInvalidAddress("Zb17ww22Kg1cqM2VykoDwZW4fCTCvxGQMb");
    }

}