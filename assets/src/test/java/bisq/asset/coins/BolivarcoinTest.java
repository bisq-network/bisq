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

public class BolivarcoinTest extends AbstractAssetTest {

    public BolivarcoinTest() {
        super(new Bolivarcoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("bFtiVn3FRKQj73h5Jv4DnZrRwdPn4cnd2M");
        assertValidAddress("bML8JAuPaAmXXzHRxJpzz1vHe3JaQbFioM");
        assertValidAddress("bEUrHZopewVJk6Ey89yohVns2gGo29NcjE");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("BPtvu628fRthbyaBG4i8AZvicnKkUsNDQJ");
        assertInvalidAddress("BFUfe1cMdoBjNFPKJyFrV36d6iLHthypXs");
        assertInvalidAddress("CQ6LkFY3ZC4xpmHZBU8KQ5RyKWorMiUpGU");
    }
}
