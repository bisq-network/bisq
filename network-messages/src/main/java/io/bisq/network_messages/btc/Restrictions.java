/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.network_messages.btc;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

public class Restrictions {

    public static final Coin MIN_TRADE_AMOUNT = Coin.parseCoin("0.0001"); // 4 cent @ 400 EUR/BTC 

    public static final Coin MAX_BUYER_SECURITY_DEPOSIT = Coin.parseCoin("0.2");
    public static final Coin MIN_BUYER_SECURITY_DEPOSIT = Coin.parseCoin("0.001");
    public static final Coin DEFAULT_BUYER_SECURITY_DEPOSIT = Coin.parseCoin("0.03");

    //TODO maybe move to separate class for constant values whcih might be changed in future by DAO voting?
    // For the seller we use a fixed one as there is no way the seller can cancel the trade
    // To make it editable would just increase complexity.
    public static final Coin SELLER_SECURITY_DEPOSIT = Coin.parseCoin("0.01");

    public static boolean isAboveDust(Coin amount, Coin txFee) {
        return amount != null && amount.compareTo(txFee.add(Transaction.MIN_NONDUST_OUTPUT)) >= 0;
    }

    public static boolean isAboveDust(Coin amount) {
        return amount != null && amount.compareTo(Transaction.MIN_NONDUST_OUTPUT) >= 0;
    }
}
