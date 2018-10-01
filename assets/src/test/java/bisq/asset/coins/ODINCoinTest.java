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

public class ODINCoinTest extends AbstractAssetTest {

    public ODINCoinTest() {
        super(new ODINCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("oSbkQG3QPxidoRRx1BatPCCHRyFXrNQv5X");
	    assertValidAddress("oVG7mEDyA9cG88siBkiQ6DEPTsdJzne8eG");
	    assertValidAddress("oLeSC73Ly7vorwj3BRJ1t7SCSV89PFGksR");
    }

    @Test
    public void testInvalidAddresses() {
	    assertInvalidAddress("XXZj1geFg6XnrgHrktsS4LiGayKmS1mUfx");
	    assertInvalidAddress("4VG7mEDyA9cG88siBkiQ6DEPTsdJzne8eG");
        assertInvalidAddress("");
	    assertInvalidAddress("16rCmCmbuWDhPjWTrpQGaU3EPdZF7MTdUk");
        assertInvalidAddress("1jRo3rcp9fjdfjdSGpx");
        assertInvalidAddress("GDARp92UtmTWDjZatG8sduRockSteadyWasHere3atrHSXr9vJzjHq2TfPrjateDz9Wc8ZJKuDayqJ$%");
        assertInvalidAddress("F3xQ8Gv6xnvDhUrM57z71bfFvu9HeofXtXpZRLnrCN2s2cKvkQowrWjJTGz4676ymKvU4NzPY8Cadgsdhsdfhg4gfJwL2yhhkJ7");
    }
}
