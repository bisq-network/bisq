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

public class Bitcoin2Test extends AbstractAssetTest {

    public Bitcoin2Test() {
        super(new Bitcoin2());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("1Ns5bawVfpHYy6J7qdANasXy2nBTtq23cg");
        assertValidAddress("1P1WG1SV9AyKsHeGZtdmh8HN6QtCmemMCV");
        assertValidAddress("1mFiSH3mHL6gdqvRXYW5BgQh9E9vLCpNE");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("21HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHa");
        assertInvalidAddress("bc1q2rskr9eey7kvuv53esm8lm2tzmejpr3yzdz8xg");
        assertInvalidAddress("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH#");
    }
}
