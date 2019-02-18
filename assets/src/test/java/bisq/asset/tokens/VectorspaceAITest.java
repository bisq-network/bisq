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

package bisq.asset.tokens;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class VectorspaceAITest extends AbstractAssetTest {

    public VectorspaceAITest () {
        super(new VectorspaceAI());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0xdd88dbdde30b684798881d4f3d9a3752d6c1dd71");
        assertValidAddress("dd88dbdde30b684798881d4f3d9a3752d6c1dd71");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("0x2ecf455d8a2e6baf8d1039204c4f97efeddf27a82");
        assertInvalidAddress("0xh8wheG1jdka0c8b8263758chanbmshj2937zgab");
        assertInvalidAddress("h8wheG1jdka0c8b8263758chanbmshj2937zgab");
    }
}
