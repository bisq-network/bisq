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

public class MaxCoinTest extends AbstractAssetTest {

    public MaxCoinTest() {
        super(new MaxCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("mJJGFhcf1PSxjxRG6DMyyi188UEXJbgZcY");
        assertValidAddress("mN1w6r2Mxkag6PU5PqpVfUdjBcgtSvp9zB");
        assertValidAddress("mPZ2HAnvJhtQysXMu8syMLNjADLkokcDhf");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("mcnZY6GyoMgkYAJWLLY95ehL9LQ4tKq4h99");
        assertInvalidAddress("mcnZY6GyoMgkYAJWLLY95ehL9L");
        assertInvalidAddress("1cnZY6GyoMgkYAJWLLY95ehL9LQ4tKq4h9");
    }
}
