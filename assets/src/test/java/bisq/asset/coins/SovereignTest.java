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

public class SovereignTest extends AbstractAssetTest {

    public SovereignTest() {
        super(new Sovereign());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("SQCTmrDAhPnrEpjBGv4exaqGBTdbNe85JY");
        assertValidAddress("SctX918ChPXd6V698sr6ZUpLB5bWYk1Jbu");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("JctX918ChPXd66V98sr6ZUpLB5bWYk1Sbu");
        assertInvalidAddress("SCtJ918ChWYd6V698ur6ZUpLB5bXXk1Pbs");
        assertInvalidAddress("Sht91dCcPX86V698sr6ZUpLBbbWYk1J5u#");
        assertInvalidAddress("XctX918ChPSd6V698sr6uUpLB5bWYJ1kbZ");
        assertInvalidAddress("9ctXS18ChPX6d86BVsr6ZUpL95bWYk1Jbu");
    }
}
