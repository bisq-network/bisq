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

public class ZcashTest extends AbstractAssetTest {

    public ZcashTest() {
        super(new Zcash());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("t1K6LGT7z2uNTLxag6eK6XwGNpdkHbncBaK");
        assertValidAddress("t1ZjdqCGEkqL9nZ8fk9R6KA7bqNvXaVLUpF");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem");
        assertInvalidAddress("38NwrYsD1HxQW5zfLT0QcUUXGMPvQgzTSn");
        assertInvalidAddress("8tP9rh3SH6n9cSLmV22vnSNNw56LKGpLrB");
        assertInvalidAddress("8Zbvjr");
    }
}
