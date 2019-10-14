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

public class LBRYCreditsTest extends AbstractAssetTest {

    public LBRYCreditsTest() {
        super(new LBRYCredits());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("bYqg2q19uWmp3waRwptzj6o8e9viHgcA9z");
        assertValidAddress("bZEnLbYb3D29Sbo8QJdiQ2PQ3En6em31gt");
        assertValidAddress("rQ26jd9mqdfPizHZUdyMjUPgK6rRANPjne");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("Don'tBeSilly");
        assertInvalidAddress("_rQ26jd9mqdfPizHZUdyMjUPgK6rRANPjne");
        assertInvalidAddress("mzYvN2WuVLyp6RZE94rzzvZwBDfCdCse6i");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem");
        assertInvalidAddress("3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX");
        assertInvalidAddress("bYqg2q19uWmp3waRwptzj6o8e9viHgcA9a");
        assertInvalidAddress("bYqg2q19uWmp3waRwptzj6o8e9viHgcA9za");
    }
}
