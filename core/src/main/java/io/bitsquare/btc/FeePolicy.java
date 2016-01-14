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
import org.bitcoinj.core.Wallet;

public class FeePolicy {

    // Official min. fee and fee per kiloByte dropped down to 0.00001 BTC / Coin.valueOf(1000) / 1000 satoshis, but as there are reported problems with 
    // confirmation we use a hgher value.
    // The should also help to avoid problems when the tx size is larger as the standard (e.g. if the user does not pay
    // in with one transaction but several tx). We don't do a dynamically fee calculation as we need predictable amounts, so that should help to get a larger 
    // headroom.
    // Andreas Schildbach reported problems with confirmation and increased the fee/offered UI side fee setting.

    // http://www.cointape.com/
    // The fastest and cheapest transaction fee is currently 50 satoshis/byte, shown in green at the top.
    // For the average transaction size of 597 bytes, this results in a fee of 298 bits (0.298 mBTC). -> 0.0003 BTC or Coin.valueOf(30000);

    // trade fee tx: 226 bytes
    // deposit tx: 336 bytes
    // payout tx: 371 bytes
    // disputed payout tx: 408 bytes -> 20400 satoshis with 50 satoshis/byte

    // Other good source is: https://tradeblock.com/blockchain 15-100 satoshis/byte

    public static final Coin TX_FEE = Coin.valueOf(20000); // 0.0002 BTC about 0.8 EUR @ 400 EUR/BTC: about 70 satoshi /byte

    static {
        Wallet.SendRequest.DEFAULT_FEE_PER_KB = TX_FEE;
    }

    public static final Coin DUST = Coin.valueOf(546);

    public static final Coin CREATE_OFFER_FEE = Coin.valueOf(100000); // 0.001 BTC  0.1% of 1 BTC about 0.4 EUR @ 400 EUR/BTC
    public static final Coin TAKE_OFFER_FEE = CREATE_OFFER_FEE;
    public static final Coin SECURITY_DEPOSIT = Coin.valueOf(10000000); // 0.1 BTC; about 40 EUR @ 400 EUR/BTC
}
