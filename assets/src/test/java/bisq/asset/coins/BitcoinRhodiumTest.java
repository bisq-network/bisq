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

public class BitcoinRhodiumTest extends AbstractAssetTest {

    public BitcoinRhodiumTest() {
        super(new BitcoinRhodium());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("RiMBe4uDXPzTxgKUEwqQobp2o7dqBDYM6S");
        assertValidAddress("RqvpFWRTSKo2QEMH89rNhs3C7CCmRRYKmg");
        assertValidAddress("Rhxz2uF9HaE2ync4eDetjkdhkS5qMXMQzz");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("Rhxz2uF9HaE2ync4eDetjkdhkS5qMXMQvdvdfbFzz");
        assertInvalidAddress("fqvpFWRTSKo2QEMH89rNhs3C7CCmRRYKmg");
        assertInvalidAddress("1HQQgsvLTgN9xD9hNmAgAreakzDsxUSLSH#");
    }
}
