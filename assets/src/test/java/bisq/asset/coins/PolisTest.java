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

public class PolisTest extends AbstractAssetTest {

    public PolisTest() {
        super(new Polis());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("PVhekAVcBTdZa6ubDJ9mup83Lg2bt374Cw");
        assertValidAddress("PQXohxv155izyStLxC77zjLKqFQe4et1q5");
        assertValidAddress("PDmRhhLKzekmKj65huGUNDALFS4EAbupWh");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("PVhekAVcBTdZa6ubDJ9mup83Lg2bt374C");
        assertInvalidAddress("pVhekAVcBTdZa6ubDJ9mup83Lg2bt374Cw");
        assertInvalidAddress("PDmRhhLKz");
        assertInvalidAddress("PDmRhhLKzekmKj65huGUNDALFS4EAbupWha");
        assertInvalidAddress("PDXohxv155izyStLxC77zjLKqFQe4et1q");
    }
}
