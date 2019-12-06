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

public class ErgoTest extends AbstractAssetTest {

    public ErgoTest() {
        super(new Ergo());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("9fRAWhdxEsTcdb8PhGNrZfwqa65zfkuYHAMmkQLcic1gdLSV5vA");
        assertValidAddress("25qGdVWg2yyYho8uC1pLtc7KxFn4nEEAwD");
        assertValidAddress("23NL9a8ngN28ovtLiKLgHexcdTKBbUMLhH");
        assertValidAddress("7bwdkU5V8");
        assertValidAddress("BxKBaHkvrTvLZrDcZjcsxsF7aSsrN73ijeFZXtbj4CXZHHcvBtqSxQ");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("9fRAWhdxEsTcdb8PhGNrZfwqa65zfkuYHAMmkQLcic1gdLSV5vAaa");
        assertInvalidAddress("25qGdVWg2yyYho8uC1pLtc7KxFn4nEEAwDaa");
        assertInvalidAddress("23NL9a8ngN28ovtLiKLgHexcdTKBbUMLhHaa");
        assertInvalidAddress("7bwdkU5V8aa");
        assertInvalidAddress("BxKBaHkvrTvLZrDcZjcsxsF7aSsrN73ijeFZXtbj4CXZHHcvBtqSxQ#");
    }
}
