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

public class BeamTest extends AbstractAssetTest {

    public BeamTest() {
        super(new Beam());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("4a0e54b24d5fdf06891a8eaa57b4b3ac16731e932a64da8ec768083495d624f1");
        assertValidAddress("c7776e6d3fd3d9cc66f9e61b943e6d99473b16418ee93f3d5f6b70824cdb7f0a9");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("114a0e54b24d5fdf06891a8eaa57b4b3ac16731e932a64da8ec768083495d624f1111111111111111");
    }
}
