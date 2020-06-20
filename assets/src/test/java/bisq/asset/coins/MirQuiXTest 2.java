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

public class MirQuiXTest extends AbstractAssetTest {

    public MirQuiXTest() {
        super(new MirQuiX());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("MCfFP5bFtN9riJiRRnH2QRkqCDqgNVC3FX");
        assertValidAddress("MEoLjNvFbNv63NtBW6eyYHUAGgLsJrpJbG");
        assertValidAddress("M84gmHb7mg4PMNBpVt3BeeAWVuKBmH6vtd");
        assertValidAddress("MNurUTgTSgg5ckmCcbjPrkgp7fekouLYgh");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("MCfFP5bFtN9riJiRRnH2QRkqCDqgNVC3FX2");
        assertInvalidAddress("MmEoLjNvFbNv63NtBW6eyYHUAGgLsJrpJbG");
        assertInvalidAddress("M84gmHb7mg4PMNBpVt3BeeAWVuKBmH63vtd");
        assertInvalidAddress("MNurUTgTSgg5ckmCcbjPrkgp7fekouLYfgh");
    }
}
