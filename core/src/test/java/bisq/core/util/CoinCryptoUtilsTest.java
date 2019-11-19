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

package bisq.core.util;

import bisq.core.util.coin.CoinUtil;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CoinCryptoUtilsTest {
    private static final Logger log = LoggerFactory.getLogger(CoinCryptoUtilsTest.class);

    @Test
    public void testGetFeePerBtc() {
        assertEquals(Coin.parseCoin("1"), CoinUtil.getFeePerBtc(Coin.parseCoin("1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.1"), CoinUtil.getFeePerBtc(Coin.parseCoin("0.1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.01"), CoinUtil.getFeePerBtc(Coin.parseCoin("0.1"), Coin.parseCoin("0.1")));
        assertEquals(Coin.parseCoin("0.015"), CoinUtil.getFeePerBtc(Coin.parseCoin("0.3"), Coin.parseCoin("0.05")));
    }

    @Test
    public void testMinCoin() {
        assertEquals(Coin.parseCoin("1"), CoinUtil.minCoin(Coin.parseCoin("1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.1"), CoinUtil.minCoin(Coin.parseCoin("0.1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.01"), CoinUtil.minCoin(Coin.parseCoin("0.1"), Coin.parseCoin("0.01")));
        assertEquals(Coin.parseCoin("0"), CoinUtil.minCoin(Coin.parseCoin("0"), Coin.parseCoin("0.05")));
        assertEquals(Coin.parseCoin("0"), CoinUtil.minCoin(Coin.parseCoin("0.05"), Coin.parseCoin("0")));
    }

    @Test
    public void testMaxCoin() {
        assertEquals(Coin.parseCoin("1"), CoinUtil.maxCoin(Coin.parseCoin("1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("1"), CoinUtil.maxCoin(Coin.parseCoin("0.1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.1"), CoinUtil.maxCoin(Coin.parseCoin("0.1"), Coin.parseCoin("0.01")));
        assertEquals(Coin.parseCoin("0.05"), CoinUtil.maxCoin(Coin.parseCoin("0"), Coin.parseCoin("0.05")));
        assertEquals(Coin.parseCoin("0.05"), CoinUtil.maxCoin(Coin.parseCoin("0.05"), Coin.parseCoin("0")));
    }

}
