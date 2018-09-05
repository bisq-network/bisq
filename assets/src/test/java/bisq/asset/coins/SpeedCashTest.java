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

public class SpeedCashTest extends AbstractAssetTest {

    public SpeedCashTest() {
        super(new SpeedCash());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("SNrVzPaFVCQGH4Rdch2EuhoyeWMfgWqk1J");
        assertValidAddress("SXPvGe87gdCFQH8zPU3JdKNGwAa4c6979r");
        assertValidAddress("STGvWjZdkwDeNoYa73cqMrAFFYm5xtJndc");
        assertValidAddress("SVPZMBgDxBVDRisdDZGD1XQwyAz8RBbo3J");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("SVPZMBgDxBVDRisdDZGD1XQwyAz8RBbo3R");
        assertInvalidAddress("mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn");
        assertInvalidAddress("XLfvvLuwjUrz2kf5gghEmUPFE3vFvwfEiL");
    }
}
