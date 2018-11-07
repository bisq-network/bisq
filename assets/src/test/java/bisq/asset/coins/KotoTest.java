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

public class KotoTest extends AbstractAssetTest {

    public KotoTest() {
        super(new Koto());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("k13dNgJJjf1SCU2Xv2jLnuUb5Q7zZx7P9vW");
        assertValidAddress("k1BGB7dreqk9yCVEjC5sqjStfRxMUHiVtTg");
        assertValidAddress("jzz6Cgk8wYy7MXZH5TCSxHbe6exCKmhXk8N");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("K1JqGEiRi3pApex3rButyuFN8HVEji9dygo");
        assertInvalidAddress("k2De32yyMZ8xdDFBJXjVseiN99S9eJpvty5");
        assertInvalidAddress("jyzCuxaXN38djCzdkb8nQs7v1joHWtkC4v8");
        assertInvalidAddress("JzyNxmc9iDaGokmMrkmMCncfMQvw5vbHBKv");
        assertInvalidAddress(
                "zkPRkLZKf4BuzBsC6r9Ls5suw1ZV9tCwiBTF5vcz2NZLUDsoXGp5rAFUjKnb7DdkFbLp7aSpejCcC4FTxsVvDxq9YKSprzf");
    }
}
