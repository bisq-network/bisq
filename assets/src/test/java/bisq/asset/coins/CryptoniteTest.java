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

public class CryptoniteTest extends AbstractAssetTest {

    public CryptoniteTest() {
        super(new Cryptonite());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("CT49DTNo5itqYoAD6XTGyTKbe8z5nGY2D5");
        assertValidAddress("CGTta3M4t3yXu8uRgkKvaWd2d8DQvDPnpL");
        assertValidAddress("Cco3zGiEJMyz3wrndEr6wg5cm1oUAbBoR2");
        assertValidAddress("CPzmjGCDEdQuRffmbpkrYQtSiUAm4oZJgt");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("CT49DTNo5itqYoAD6XTGyTKbe8z5nGY2D4");
        assertInvalidAddress("CGTta3M4t3yXu8uRgkKvaWd2d8DQvDPnpl");
        assertInvalidAddress("Cco3zGiEJMyz3wrndEr6wg5cm1oUAbBoR1");
        assertInvalidAddress("CPzmjGCDEdQuRffmbpkrYQtSiUAm4oZJgT");
        assertInvalidAddress("CT49DTNo5itqYoAD6XTGyTKbe8z5nGY2Da");
        assertInvalidAddress("asdasd");
        assertInvalidAddress("cT49DTNo5itqYoAD6XTGyTKbe8z5nGY2Da");
        assertInvalidAddress("No5itqYoAD6XTGyTKbe8z5nGY2Da");
    }
}
