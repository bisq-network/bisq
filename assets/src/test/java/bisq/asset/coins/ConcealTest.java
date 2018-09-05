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

public class ConcealTest extends AbstractAssetTest {

    public ConcealTest() {
        super(new Conceal());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("ccx7EmJzRoQ1CaQKxrJZSL87pT2MEcj2BTVAoznrnaLmUEnvBGSh5RrBngsJS4qa1N8daS7fy6gVgYwj8Ao5H6YQ8vqwDAq8P5");
        assertValidAddress("ccx7Xd3NBbBiQNvv7vMLXmGMHyS8AVB6EhWoHo5EbGfR2Ki9pQnRTfEBt3YxYEVqpUCyJgvPjBYHp8N2yZwA7dqb4PjaGWuvs4");
        assertValidAddress("ccx7XzWDecUfTRKbkXwENTGr53Q9tWNdwGF7C7Nj9ZFybHxEw2DxPeuKgJrzscFKi917SG5wL5AYHGFUrMDqSPnC5Apr8KwbD6");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("CCX7EmJzRoQ1CaQKxrJZSL87pT2MEcj2BTVAoznrnaLmUEnvBGSh5RrBngsJS4qa1N8daS7fy6gVgYwj8Ao5H6YQ8vqwDAq8P5");
        assertInvalidAddress("ccx6EmJzRoQ1CaQKxrJZSL87pT2MEcj2BTVAoznrnaLmUEnvBGSh5RrBngsJS4qa1N8daS7fy6gVgYwj8Ao5H6YQ8vqwDAq8P5");
        assertInvalidAddress("ccx7EmJzRoQ1CaQKxrJZSL87pT2MEcj2BTVAoznrnaLmUEnvBGSh5RrBngsJS4qa1N8daS7fy6gVgYwj8Ao5H6YQ8vqwDAq8P5x");
        assertInvalidAddress("ccx7EmJzRoQ1CaQKxrJZSL87pT2MEcj2BTVAoznrnaLmUEnvBGSh5RrBngsJS4qa1N8daS7fy6gVgYwj8Ao5H6YQ8vqwDAq8P");
        assertInvalidAddress("ccx7EmJzRoQ1CaQKxrJZSL87pT2MEcj2BTVAoznrnaLmUEnvBGSh5RrBngsJS4qa1N8daS7fy6gVgYwj8Ao5H6YQ8vqwDAq8P0");
        assertInvalidAddress("ccx7EmJzRoQ1CaQKxrJZSL87pT2MEcj2BTVAoznrnaLmUEnvBGSh5RrBngsJS4qa1N8daS7fy6gVgYwj8Ao5H6YQ8vqwDAq8PO");
        assertInvalidAddress("ccx7EmJzRoQ1CaQKxrJZSL87pT2MEcj2BTVAoznrnaLmUEnvBGSh5RrBngsJS4qa1N8daS7fy6gVgYwj8Ao5H6YQ8vqwDAq8PI");
        assertInvalidAddress("ccx7EmJzRoQ1CaQKxrJZSL87pT2MEcj2BTVAoznrnaLmUEnvBGSh5RrBngsJS4qa1N8daS7fy6gVgYwj8Ao5H6YQ8vqwDAq8Pl");
    }
}
