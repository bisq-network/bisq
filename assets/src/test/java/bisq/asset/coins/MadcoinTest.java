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

public class MadcoinTest extends AbstractAssetTest {

    public MadcoinTest() {
        super(new Madcoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("mHUisRLQ4vMXrWrVfGfiEHuD3KZqiUNvzH");
        assertValidAddress("mHbicWaTXNJDbeM6KXCit5JcmvMypwpq8T");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("LzBc4XEFSdzCDcTxAgf6EZXgsZWpztRhe");
        assertInvalidAddress("miCVC7QcY917Cz427qTB");
        assertInvalidAddress("12KYrjTdVGjFMtaxERSk3gphreJ5US8aUP");
    }
}
