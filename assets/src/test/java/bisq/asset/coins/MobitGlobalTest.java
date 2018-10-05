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

public class MobitGlobalTest extends AbstractAssetTest {

    public MobitGlobalTest() {
        super(new MobitGlobal());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("MKDLXTdJs1AtAJhoRddLBSimXCE6SXbyMq");
        assertValidAddress("MGr2WYY9kSLPozEcsCWSEumXNX2AJXggUR");
        assertValidAddress("MUe1HzGqzcunR1wUxHTqX9cuQNMnEjiN7D");
        assertValidAddress("MWRqbYKkQcSvtHq4GFrPvYGf8GFGsLNPcE");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("AWGfbG22DNhgP2rsKfqyFxCwi1u68BbHAA1");
        assertInvalidAddress("AWGfbG22DNhgP2rsKfqyFxCwi1u68BbHAB");
        assertInvalidAddress("AWGfbG22DNhgP2rsKfqyFxCwi1u68BbHA#");
    }
}
