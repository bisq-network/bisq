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

public class InfinityEconomicsTest extends AbstractAssetTest {

    public InfinityEconomicsTest() {
        super(new InfinityEconomics());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("XIN-FXFA-LR6Y-QZAW-9V4SX");
        assertValidAddress("XIN-JM2U-U4AE-G7WF-3NP9F");
        assertValidAddress("XIN-2223-2222-KB8Y-22222");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("abcde");
        assertInvalidAddress("XIN-");
        assertInvalidAddress("XIN-FXFA-LR6Y-QZAW-9V4SXA");
        assertInvalidAddress("NIX-FXFA-LR6Y-QZAW-9V4SX");
        assertInvalidAddress("XIN-FXF-LR6Y-QZAW-9V4SX");
        assertInvalidAddress("XIN-FXFA-LR6-QZAW-9V4SX");
        assertInvalidAddress("XIN-FXFA-LR6Y-QZA-9V4SX");
        assertInvalidAddress("XIN-FXFA-LR6Y-QZAW-9V4S");
    }
}
