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

public class QRLTest extends AbstractAssetTest {

    public QRLTest() {
        super(new QRL());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("Q0104008e2b38425dd2bae2b2b3a88d8df4911b0e0e5e880a71abe9e0f68296cc3560fb52dfb637");
        assertValidAddress("Q0204003808ebc69dfb4d9da48ec06bd7682091589aa4f6d7040d1f26ee1bf947e9f19fa50d253f");
        assertValidAddress("Q00050049194cc61c011dc0bccdfdccefc78cf540544520e283457ede9d3349d074883fc88497bb");
        assertValidAddress("Q010400e6f61e0a86b48e49ff34e5f7837d19e11a69aad2e8d49c1bb625bbc8f076823288a2b38b");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("Z0104008e2b38425dd2bae2b2b3a88d8df4911b0e0e5e880a71abe9e0f68296cc3560fb52dfb637");
        assertInvalidAddress("Q01A4008e2b38425dd2bae2b2b3a88d8df4911b0e0e5e880a71abe9e0f68296cc3560fb52dfb637");
        assertInvalidAddress("Q0104008e2b38425dd2bae2b2b3a88d8df4911b0e0e5e880a71abe9e0fR8296cc3560fb52dfb637");
        assertInvalidAddress("Q0104008e2b38425dd2bae2b2b3a88d8df491?b0e0e5e880a71abe9e0fR8296cc3560fb52dfb637");
        assertInvalidAddress("Q010400e6f61e0a86b48e49ff34e5f7837d19e11a69aad2e8d49c1bb625bbc8f076823288a2b");
        assertInvalidAddress("");
    }
}
