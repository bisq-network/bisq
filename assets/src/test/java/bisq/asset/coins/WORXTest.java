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

public class WORXTest extends AbstractAssetTest {

    public WORXTest() {
        super(new WORX());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("WgeBjv4PkmNnsUZ6QqhhT3ynEaqr3xDWuS");
        assertValidAddress("WQDes3h9GBa72R9govQCic3f38m566Jydo");
        assertValidAddress("WeNnnz8KFgmipcLhpbXSM9HT37pSqqeVbk");
        assertValidAddress("WNzf7fZgc2frhBGqVvhVhYpSBMWd2WE6x5");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("WgeBjv4PksmNnsUZ6QqhhT3ynEaqr3xDWuS");
        assertInvalidAddress("W2QDes3h9GBa72R9govQCic3f38m566Jydo");
        assertInvalidAddress("WeNnnz8KFgmipcLhpbXSM9HT37pSqqeVbk3");
        assertInvalidAddress("WNzf7fZgc2frhBGqVvhVhYpSBMWd2WE6x54");
    }
} 