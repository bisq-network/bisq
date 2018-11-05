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

public class DixiTest extends AbstractAssetTest {

    public DixiTest() {
        super(new Dixi());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("D6ACM1W7pNM8viieg8Gz5tyoKx7BZgkLXZ");
        assertValidAddress("D6Ukfw951GiHMF3L3yDf3EeGwxLuKJXboe");
        assertValidAddress("DSmhYbbPboM9RVgF6y1sdT3QfEySUT4k7J");
        assertValidAddress("DGC3mwGUrPiuNsA72V7EAQqHgdCWY2nJrE");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("D6UkfW951GIHMf3L3yDf3EeGwxLuKJXboe");
        assertInvalidAddress("DSmhYb5PboM9RVgF6y1sdT3qfEySUT4k7j");
        assertInvalidAddress("D6ACM1W7pNM8viieg8Gz5tyoKx7BZgkLX#");
    }
}
