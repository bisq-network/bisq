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

public class BitcoinGoldTest extends AbstractAssetTest {

    public BitcoinGoldTest() {
        super(new BitcoinGold());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("AehvQ57Fp168uY592LCUYBbyNEpiRAPufb");
        assertValidAddress("GWaSW6PHfQKBv8EXV3xiqGG2zxKZh4XYNu");
        assertValidAddress("GLpT8yG2kwPMdMfgwekG6tEAa91PSmN4ZC");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("GVTPWDVJgLxo5ZYSPXPDxE4s7LE5cLRwCc1");
        assertInvalidAddress("1GVTPWDVJgLxo5ZYSPXPDxE4s7LE5cLRwCc");
        assertInvalidAddress("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH");
    }
}
