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

public class StarwelsTest extends AbstractAssetTest {

    public StarwelsTest() {
        super(new Starwels());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("1F7EixuiBdvi9bVxEPzAgJ11GRJsdH3ihh");
        assertValidAddress("17DdVnWvz3XZPvMYHmSRSycUgt2EEv29So");
        assertValidAddress("1HuoFLoGJQCLodNDH5oCXWaR1kL8DwksJX");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("21HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHa");
        assertInvalidAddress("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHs");
        assertInvalidAddress("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH#");
    }
}
