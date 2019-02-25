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

package bisq.core.btc.wallet;

import bisq.core.app.BisqEnvironment;

import org.bitcoinj.core.Coin;

public class Restrictions {
    private static Coin MIN_TRADE_AMOUNT;
    private static Coin MAX_BUYER_SECURITY_DEPOSIT;
    private static Coin MIN_BUYER_SECURITY_DEPOSIT;
    private static Coin DEFAULT_BUYER_SECURITY_DEPOSIT;
    // For the seller we use a fixed one as there is no way the seller can cancel the trade
    // To make it editable would just increase complexity.
    private static Coin SELLER_SECURITY_DEPOSIT;

    public static Coin getMinNonDustOutput() {
        if (minNonDustOutput == null)
            minNonDustOutput = BisqEnvironment.getBaseCurrencyNetwork().getParameters().getMinNonDustOutput();
        return minNonDustOutput;
    }

    private static Coin minNonDustOutput;

    public static boolean isAboveDust(Coin amount) {
        return amount.compareTo(getMinNonDustOutput()) >= 0;
    }

    public static boolean isDust(Coin amount) {
        return !isAboveDust(amount);
    }

    public static Coin getMinTradeAmount() {
        if (MIN_TRADE_AMOUNT == null)
            MIN_TRADE_AMOUNT = Coin.valueOf(10_000); // 2 USD @ 20000 USD/BTC
        return MIN_TRADE_AMOUNT;
    }

    // Can be reduced but not increased. Otherwise would break existing offers!
    public static Coin getMaxBuyerSecurityDeposit() {
        if (MAX_BUYER_SECURITY_DEPOSIT == null)
            MAX_BUYER_SECURITY_DEPOSIT = Coin.valueOf(5_000_000); // 1000 USD @ 20000 USD/BTC
        return MAX_BUYER_SECURITY_DEPOSIT;
    }

    public static Coin getMinBuyerSecurityDeposit() {
        if (MIN_BUYER_SECURITY_DEPOSIT == null)
            MIN_BUYER_SECURITY_DEPOSIT = Coin.valueOf(50_000); // 10 USD @ 20000 USD/BTC
        return MIN_BUYER_SECURITY_DEPOSIT;
    }

    public static Coin getDefaultBuyerSecurityDeposit() {
        if (DEFAULT_BUYER_SECURITY_DEPOSIT == null)
            DEFAULT_BUYER_SECURITY_DEPOSIT = Coin.valueOf(1_000_000); // 200 EUR @ 20000 USD/BTC
        return DEFAULT_BUYER_SECURITY_DEPOSIT;
    }

    public static Coin getSellerSecurityDeposit() {
        if (SELLER_SECURITY_DEPOSIT == null)
            SELLER_SECURITY_DEPOSIT = Coin.valueOf(300_000); // 60 USD @ 20000 USD/BTC
        return SELLER_SECURITY_DEPOSIT;
    }
}
