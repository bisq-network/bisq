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

import org.junit.Test;
import bisq.asset.AbstractAssetTest;

public class DoichainTest extends AbstractAssetTest {

    public DoichainTest() {
        super(new Doichain());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("NGHV9LstnZfrkGx5QJmYhEepbzc66W7LN5");
        assertValidAddress("N4jeY9YhU49qHN5wUv7HBxeVZrFg32XFy7");
        assertValidAddress("6a6xk7Ff6XbgrNWhSwn7nM394KZJNt7JuV");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("NGHV9LstnZfrkGx5QJmYhEepbzc66W7LN5x"); 
        assertInvalidAddress("16iWWt1uoG8Dct56Cq6eKHFxvGSDha46Lo"); 
        assertInvalidAddress("38BFQkc9CdyJUxQK8PhebnDcA1tRRwLDW4"); 
    }
}
