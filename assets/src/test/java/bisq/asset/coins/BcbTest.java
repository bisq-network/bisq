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

public class BcbTest extends AbstractAssetTest {
    public BcbTest() {
        super(new Bcb());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("bcb_1fuckbtc6p55wt64eo4rz7brq3ubjfd8unhz3it5fbdpta8tww7ywk8p9su7");
    }

    @Override
    public void testInvalidAddresses() {
        //exceed the limit
        assertInvalidAddress("bcb_1j78msn5omp8jrjge8txwxm4x3smusa1cojg7nuk8fdzoux41fqeeogg5aa111");
        //invalid prefix
        assertInvalidAddress("cda_1j78msn5omp8jrjge8txwxm4x3smusa1cojg7nuk8fdzoux41fqeeogg5aa1");
        //not valid address
        assertInvalidAddress("");
        assertInvalidAddress("not is an address");
    }
}
