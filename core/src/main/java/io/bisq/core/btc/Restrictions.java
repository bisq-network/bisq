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

package io.bisq.core.btc;

import io.bisq.core.app.BisqEnvironment;
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

    public static boolean isAboveDust(Coin amount, Coin txFee) {
        return amount != null && amount.compareTo(txFee.add(getMinNonDustOutput())) >= 0;
    }

    public static boolean isAboveDust(Coin amount) {
        return amount != null && amount.compareTo(getMinNonDustOutput()) >= 0;
    }

    public static Coin getMinTradeAmount() {
        if (MIN_TRADE_AMOUNT == null)
            switch (BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode()) {
                case "BTC":
                    MIN_TRADE_AMOUNT = Coin.valueOf(10_000); // 2 USD @ 20000 USD/BTC
                    break;
                case "LTC":
                    MIN_TRADE_AMOUNT = Coin.valueOf(100_000); // 0.04 EUR @ 40 EUR/LTC
                    break;
                case "DOGE":
                    MIN_TRADE_AMOUNT = Coin.valueOf(1_000_000_000L); // 0.03 EUR at DOGE price 0.003 EUR;
                    break;
                case "DASH":
                    MIN_TRADE_AMOUNT = Coin.valueOf(20_000L); // 0.03 EUR at @ 150 EUR/DASH;
                    break;
            }
        return MIN_TRADE_AMOUNT;
    }

    // Can be reduced but not increased. Otherwise would break existing offers!
    public static Coin getMaxBuyerSecurityDeposit() {
        if (MAX_BUYER_SECURITY_DEPOSIT == null)
            switch (BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode()) {
                case "BTC":
                    MAX_BUYER_SECURITY_DEPOSIT = Coin.valueOf(5_000_000); // 1000 USD @ 20000 USD/BTC
                    break;
                case "LTC":
                    MAX_BUYER_SECURITY_DEPOSIT = Coin.valueOf(1_200_000_000); // 500 EUR @ 40 EUR/LTC
                    break;
                case "DOGE":
                    MAX_BUYER_SECURITY_DEPOSIT = Coin.valueOf(20_000_000_000_000L); // 500 EUR @ 0.0025 EUR/DOGE;
                    break;
                case "DASH":
                    MAX_BUYER_SECURITY_DEPOSIT = Coin.valueOf(300_000_000L); // 450 EUR @ 150 EUR/DASH;
                    break;
            }

        return MAX_BUYER_SECURITY_DEPOSIT;
    }

    public static Coin getMinBuyerSecurityDeposit() {
        if (MIN_BUYER_SECURITY_DEPOSIT == null)
            switch (BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode()) {
                case "BTC":
                    MIN_BUYER_SECURITY_DEPOSIT = Coin.valueOf(50_000); // 10 USD @ 20000 USD/BTC
                    break;
                case "LTC":
                    MIN_BUYER_SECURITY_DEPOSIT = Coin.valueOf(6_000_000); // 2.4 EUR @ 40 EUR/LTC
                    break;
                case "DOGE":
                    MIN_BUYER_SECURITY_DEPOSIT = Coin.valueOf(100_000_000_000L); // 2.5 EUR @ 0.0025 EUR/DOGE;
                    break;
                case "DASH":
                    MIN_BUYER_SECURITY_DEPOSIT = Coin.valueOf(1_500_000L); // 2.5 EUR @ 150 EUR/DASH;
                    break;
            }
        return MIN_BUYER_SECURITY_DEPOSIT;
    }

    public static Coin getDefaultBuyerSecurityDeposit() {
        if (DEFAULT_BUYER_SECURITY_DEPOSIT == null)
            switch (BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode()) {
                case "BTC":
                    DEFAULT_BUYER_SECURITY_DEPOSIT = Coin.valueOf(1_000_000); // 200 EUR @ 20000 USD/BTC
                    break;
                case "LTC":
                    DEFAULT_BUYER_SECURITY_DEPOSIT = Coin.valueOf(200_000_000); // 75 EUR @ 40 EUR/LTC
                    break;
                case "DOGE":
                    DEFAULT_BUYER_SECURITY_DEPOSIT = Coin.valueOf(3_000_000_000_000L); // 75 EUR @ 0.0025 EUR/DOGE;
                    break;
                case "DASH":
                    DEFAULT_BUYER_SECURITY_DEPOSIT = Coin.valueOf(50_000_000L); // 75 EUR @ 150 EUR/DASH;
                    break;
            }
        return DEFAULT_BUYER_SECURITY_DEPOSIT;
    }

    public static Coin getSellerSecurityDeposit() {
        if (SELLER_SECURITY_DEPOSIT == null)
            switch (BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode()) {
                case "BTC":
                    SELLER_SECURITY_DEPOSIT = Coin.valueOf(300_000); // 60 USD @ 20000 USD/BTC
                    break;
                case "LTC":
                    SELLER_SECURITY_DEPOSIT = Coin.valueOf(60_000_000); // 25 EUR @ 40 EUR/LTC
                    break;
                case "DOGE":
                    SELLER_SECURITY_DEPOSIT = Coin.valueOf(1_000_000_000_000L); // 25 EUR @ 0.0025 EUR/DOGE;
                    break;
                case "DASH":
                    SELLER_SECURITY_DEPOSIT = Coin.valueOf(15_000_000L); // 25 EUR @ 150 EUR/DASH;
                    break;
            }
        return SELLER_SECURITY_DEPOSIT;
    }
}
