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

public class LitecoinExtremeTest extends AbstractAssetTest {

    public LitecoinExtremeTest(){
        super(new LitecoinExtreme());
    }

    @Test
    public void testValidAddresses(){
        assertValidAddress("ESLeu9xKjXxNgcTMcgXXZxbSYsMgTtuMN7");
        assertValidAddress("EeC7WvgGovnh1WhuszAs7ywmAdsXhSUNsK");
        assertValidAddress("EX54wDfRkEmhtRhnNvva429UK4W8JAL98j");
    }

    @Test
    public void testInvalidAddresses(){
        assertInvalidAddress("EULJtmeDrWpNqNyHy6rhubxY5B8RRF5ZRF1");
        assertInvalidAddress("LEJFUeEcfV5obFAiBsHm2HxJaw3KEb3PD1a");
        assertInvalidAddress("");
        assertInvalidAddress("Ea9XHztCQBTFWNyz4Yneoh7VSFSMF#6oo81");
        assertInvalidAddress("EVCCEVdFNxxbiWDZ7ZGoLEBbi3n2i483ABdsdsdsadfsafdas");
        assertInvalidAddress("EScnDkuPKzhNixJkVrUfytE2U2uVk3nSq9112222");
        assertInvalidAddress("GEc3ZPa5Xza6KuEY8orrfSCTjNGA8cBr6mg");
        assertInvalidAddress("ELwSkdFmd2YMuVtHVk1ywMxY1CiPSXVy9E$%");
    }
}
