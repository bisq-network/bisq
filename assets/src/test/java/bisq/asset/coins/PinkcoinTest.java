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

public class PinkcoinTest extends AbstractAssetTest {

    public PinkcoinTest() {
        super(new Pinkcoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("2KZEgvipDn5EkDAFB8UR8nVXuKuKt8rmgH");
        assertValidAddress("2KVgwafcbw9LcJngqAzxu8UKpQSRwNhtTH");
        assertValidAddress("2TPDcXRRmvTxJQ4V8xNhP1KmrTmH9KKCkg");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("PPo1gCi4xoC87gZZsnU2Uj6vSgZAAD9com");
        assertInvalidAddress("z4Vg3S5pJEJY45tHX7u6X1r9tv2DEvCShi2");
        assertInvalidAddress("1dQT9U73rNmomYkkxQwcNYhfQr9yy4Ani");
    }
}
