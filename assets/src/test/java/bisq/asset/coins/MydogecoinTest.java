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

import org.junit.jupiter.api.Test;

public class MydogecoinTest extends AbstractAssetTest {

    public MydogecoinTest() {
        super(new Mydogecoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("MYYVuV9Jnt4ZHqX6yEBzLvRNBL8rpXYLkU");
        assertValidAddress("MbwEhEzzXjuYmaaN2Q4uYL1JNgch7J2Z8c");
        assertValidAddress("MqiykXzzpAYBuPLGtRvbqsUtujEiZGTe7X");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("MYYVuV9Jnt4ZHqX6yEBzLvRNBL000XYLkU");
        assertInvalidAddress("MbwEhEzzXjuYmaaN2Q4uYL1JNgch7J2Z8#");
        assertInvalidAddress("MqiykXzzpAYBuPLGtRvbqsUtujEiZGTe7#");
    }
}
