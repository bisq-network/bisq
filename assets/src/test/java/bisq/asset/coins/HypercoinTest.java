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

import org.junit.jupiter.api.Test;

public class HypercoinTest extends AbstractAssetTest {

    public HypercoinTest() {
        super(new Hypercoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("193rdaPZVXyxJtQ2yCP2nZX5BA13ijG7Yt");
        assertValidAddress("hc1qre8fp627d2evr2jsrzd3nf8k2lzm6535ucgekl");
        assertValidAddress("3PB4Zrd7BqYFFCmaWFFg5A8VDsHwRcPD5x");
    }

    @Test
    public void testInvalidAddresses() {
        assertValidAddress("193rdaPZVXyxJtQ2yCP2nZX5BA13ixx7Yt");
        assertValidAddress("hc1qre8fp627d2evr2jsrzd3nf8k55zm6535ucgekl");
        assertValidAddress("3PB4Zrd7BqYFFCmaWFFg5A8VDsHwRcPD5#");
    }
}
