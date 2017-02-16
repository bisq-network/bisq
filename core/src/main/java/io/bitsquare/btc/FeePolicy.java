/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc;

import org.bitcoinj.core.Coin;

public class FeePolicy {

    // With block getting filled up the needed fee to get fast into a black has become more expensive and less predictable.
    // To see current fees check out:
    // https://tradeblock.com/blockchain
    // https://jochen-hoenicke.de/queue/24h.html
    // https://bitcoinfees.21.co/
    // http://p2sh.info/dashboard/db/fee-estimation
    // https://bitcoinfees.github.io/#1d
    // https://estimatefee.appspot.com/
    // Average values are 10-100 satoshis/byte in january 2016
    // Average values are 60-140 satoshis/byte in february 2017

    private static Coin NON_TRADE_FEE_PER_KB = Coin.valueOf(150_000);

    public static void setNonTradeFeePerKb(Coin nonTradeFeePerKb) {
        NON_TRADE_FEE_PER_KB = nonTradeFeePerKb;
    }

    public static Coin getNonTradeFeePerKb() {
        return NON_TRADE_FEE_PER_KB;
    }

    public static Coin getDefaultSecurityDeposit() {
        return Coin.valueOf(3_000_000);
    }

}
