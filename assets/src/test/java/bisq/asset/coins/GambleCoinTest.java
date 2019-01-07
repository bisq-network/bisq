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

public class GambleCoinTest extends AbstractAssetTest {

    public GambleCoinTest() {
        super(new GambleCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("CKWCoP2Cog4gU3ARzNqGEqwDxNZNVEpPJP");
        assertValidAddress("CJmvkF84bW93o5E7RFe4VzWMSt4WcKo1nv");
        assertValidAddress("Caz2He7kZ8ir52CgAmQywCjm5hRjo3gLwT");
        assertValidAddress("CM2fRpzpxqyRvaWxtptEmRzpGCFE1qCA3V");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("CKWCoP2C0g4gU3ARzNqGEqwDxNZNVEpPJP");
        assertInvalidAddress("CJmvkF84bW93o5E7RFe4VzWMSt4WcKo1nvx");
        assertInvalidAddress("Caz2He7kZ8ir52CgAmQywC#m5hRjo3gLwT");
        assertInvalidAddress("DM2fRpzpxqyRvaWxtptEmRzpGCFE1qCA3V");
    }
}
