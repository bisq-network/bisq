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

public class LobstexTest extends AbstractAssetTest {

    public LobstexTest() {
        super(new Lobstex());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("LbfvBwhBKK9EUnBEzCa4spT6RgEKXj3RoR");
        assertValidAddress("LMMLeypCrMzxytAUfTBpLDQ98eRx7hXRgD");
        assertValidAddress("LeGSV3RRb7zULou9XGZsUUGLLk3gPi41aV");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("LbfvBwhBKK9EUnBEzCa4spT6RgEKXj3RoRX");
        assertInvalidAddress("LbfvBwhBKK9EUnBEzCa4spT6Rg");
        assertInvalidAddress("LbfvBwhBKK9EUnBEzCa4spT6RgEKXj3Ro#");
        assertInvalidAddress("ObfvBwhBKK9EUnBEzCa4spT6RgEKXj3RoR");
    }
}
