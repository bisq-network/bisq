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

package bisq.asset.tokens;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class AugmintEuroTest extends AbstractAssetTest {

    public AugmintEuroTest() {
        super(new AugmintEuro());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0x0d81d9e21bd7c5bb095535624dcb0759e64b3899");
        assertValidAddress("0d81d9e21bd7c5bb095535624dcb0759e64b3899");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("0x65767ec6d4d3d18a200842352485cdc37cbf3a216");
        assertInvalidAddress("0x65767ec6d4d3d18a200842352485cdc37cbf3a2g");
        assertInvalidAddress("65767ec6d4d3d18a200842352485cdc37cbf3a2g");
    }
}
