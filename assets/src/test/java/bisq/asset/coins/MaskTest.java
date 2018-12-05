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

public class MaskTest extends AbstractAssetTest {

    public MaskTest() {
        super(new Mask());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("MbxYjp38aUXBuESwsFv8YmRvbQvMhNyJygU6ViLCjM4sUNqFjsHQim9dvzp9p8BVTjdsRkVNrC1Zy3NJRb18hav3CPe5eWn");
        assertValidAddress("MeGcanFnSr4bJFuNoHogCBdDCsqDrNu5njPc1Yh1DfsTUTL5dLbbtE119f4vztxXu6fFCKWRmpqjABdDyGrzMDkhTC38WwS");
        assertValidAddress("bTWEbW8kKVrZkDwyPs5t7BZXotMNyz5UY2QDJ6MjKT7ihA8kNKhoHDqPUiUB7jPxNpXLFkJsgL6TA1fo7yAzVUdm1hTopCocf");
        assertValidAddress("bTXejHgtfTLWzhyz9fHHBDKTWrsM8MKnebZCKeue8mbDWaKRhnQ8VisGRXUgTvUhsDiwX6PxeP5A22DFf5UVEk431Vjt8m3GM");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("MsopefFnSr4bJFuNoHogCBdDCsqDrNu5Pc1Yh1DfsTUTL5dLbbtE119f4vztxXu6fFCKWRmpqjABdDyGrzMDkhTC38gWw");
        assertInvalidAddress("MeGcanuyt4bJFuNoHogCBdDCsqDrNu5njPc1Yh1DfsTUTL5dLbbtE119f4vztxXu6fFCKWRmpqujABdDyGrzMDkhTC38WwSx");
        assertInvalidAddress("MrtcanFnSr4bJFuNoHogCBdDCsqDrNu5Pc1Yh1DfsTUTL5dLbbtE119f4vztxXu6fFCKWRmpqjABdDyGrzMDkhTC3rt4vb8Ww");
        assertInvalidAddress("bBXejHgtfTLWzhyz9fHKBDKTWrsM8MKnebZCKeue8mbDWaKRhnQ8VisGRXUgTvUhsDiwX6PxeP5A22DFf5UVEk431Vjt8m3GM");
    }
}
