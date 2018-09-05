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

public class KumacoinTest extends AbstractAssetTest {

    public KumacoinTest() {
        super(new Kumacoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("KKeZ46ELdrcnB9TT8R7GiU3cdd29gA6mQm");
        assertValidAddress("4FmFUcHecwkepmAtwFZoJGJUnQsLMqKY3k");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("KKeZ46ELdrcnB9TT8R7GiU3cdd29gA6mQmm");
        assertInvalidAddress("KKeZ46ELdrcnB9TT8R7GiU3cdd29gA6mQ");
        assertInvalidAddress("4FmFUcHecwkepmAtwFZoJGJUnQsLMqKY3k#");
    }
}
