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

public class LiquidBitcoinTest extends AbstractAssetTest {


    public LiquidBitcoinTest() {
        super(new LiquidBitcoin());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("VJL6mu5gqT4pRzpd28Y6aXg9murwJpd25EBwMtrnCN82n6z6i5kMLKnN63nyaCgRuJWZu4EFFV7gp9Yb");
        assertValidAddress("Gq3AeVacy6EUWSJKsV4NScyYKvnM6Gf8We");
    }

    @Override
    public void testInvalidAddresses() {
        assertInvalidAddress("lq1qqgu6g99aa4y7fly26gwj3k69t2kgx8eshn8gqacsul9yhpcgtvweyzuqt6cn3fjawvwaluq6ls6t9qnvg4jgwffyycwmgqh0h"); //no native segwit address support
        assertInvalidAddress("lq1qqgu6g99aa4y7fly26gwj3k69t2kgx8eshn8gqacsul9yhpcgtvweyzuqt6cn3fjawvwaluq6ls6t9qnvg4jgwffyycwmgqsdf");
    }
}
