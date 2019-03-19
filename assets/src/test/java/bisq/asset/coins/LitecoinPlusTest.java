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

public class LitecoinPlusTest extends AbstractAssetTest {

    public LitecoinPlusTest() {
        super(new LitecoinPlus());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("XGnikpGiuDTaxq9vPfDF9m9VfTpv4SnNN5");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW");
        assertInvalidAddress("LgfapHEPhZbdRF9pMd5WPT35hFXcZS1USrW");
        assertInvalidAddress("LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW#");
    }
}

