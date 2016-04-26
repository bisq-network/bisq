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
    // http://www.cointape.com
    // Average values are 10-100 satoshis/byte in january 2016
    // 
    // Our trade transactions have a fixed set of inputs and outputs making the size very predictable 
    // (as long the user does not do multiple funding transactions)
    // 
    // trade fee tx: 226 bytes
    // deposit tx: 336 bytes
    // payout tx: 371 bytes
    // disputed payout tx: 408 bytes

    // We set a fixed fee to make the needed amounts in the trade predictable.
    // We use 0.0002 BTC (0.08 EUR @ 400 EUR/BTC) which is for our tx sizes about 50-90 satoshi/byte
    // We cannot make that user defined as it need to be the same for both users, so we can only change that in 
    // software updates 
    // TODO before Beta we should get a good future proof guess as a change causes incompatible versions
    public static Coin getFixedTxFeeForTrades() {
        return Coin.valueOf(20_000);
    }

    // For non trade transactions (withdrawal) we use the default fee calculation 
    // To avoid issues with not getting into full blocks, we increase the fee/kb to 30 satoshi/byte
    // The user can change that in the preferences 
    // The BitcoinJ fee calculation use kb so a tx size  < 1kb will still pay the fee for a kb tx.
    // Our payout tx has about 370 bytes so we get a fee/kb value of about 90 satoshi/byte making it high priority
    // Other payout transactions (E.g. arbitrators many collected transactions) will go with 30 satoshi/byte if > 1kb
    private static Coin NON_TRADE_FEE_PER_KB = Coin.valueOf(10_000); // 0.0001 BTC about 0.04 EUR @ 400 EUR/BTC 

    public static void setNonTradeFeePerKb(Coin nonTradeFeePerKb) {
        NON_TRADE_FEE_PER_KB = nonTradeFeePerKb;
    }

    public static Coin getNonTradeFeePerKb() {
        return NON_TRADE_FEE_PER_KB;
    }

    // 0.0005 BTC  0.05% of 1 BTC about 0.2 EUR @ 400 EUR/BTC
    public static Coin getCreateOfferFee() {
        // We need to pay the quite high miner fee of 30_000 from the trading fee tx so 30_000 us our lower limit
        // The arbitrator receive only 0.0002 BTC - less than the miners
        return Coin.valueOf(50_000);
    }

    // 0.001 BTC  0.1% of 1 BTC about 0.4 EUR @ 400 EUR/BTC
    public static Coin getTakeOfferFee() {
        return Coin.valueOf(100_000);
    }


    // TODO will be increased once we get higher limits
    // 0.01 BTC; about 4 EUR @ 400 EUR/BTC
    public static Coin getSecurityDeposit() {
        return Coin.valueOf(1_000_000);
    }
}
