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

public class FaircoinTest extends AbstractAssetTest {

    public FaircoinTest() {
        super(new Faircoin());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("fLsJC1Njap5NxSArYr5wCJbKBbTQfWikY6");
        assertValidAddress("FZHzHraqjty2Co7TinwcsBtPKoz5ANvgRd");
        assertValidAddress("fHbXBBBjU1xxEVmWEtAEwXnoBDxxsxfvxg");
    }

    @Override
    public void testInvalidAddresses() {
        assertInvalidAddress("FLsJC1Njap5NxSArYr5wCJbKBbTQfWikY6");
        assertInvalidAddress("fZHzHraqjty2Co7TinwcsBtPKoz5ANvgRd");
        assertInvalidAddress("1HbXBBBjU1xxEVmWEtAEwXnoBDxxsxfvxg");
    }
}
