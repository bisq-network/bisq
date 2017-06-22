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

package io.bisq.core.btc;

import io.bisq.core.app.BisqEnvironment;
import org.bitcoinj.core.Coin;

public class Restrictions {

    private static Coin MIN_TRADE_AMOUNT;

    private static Coin MAX_BUYER_SECURITY_DEPOSIT;
    private static Coin MIN_BUYER_SECURITY_DEPOSIT;
    private static Coin DEFAULT_BUYER_SECURITY_DEPOSIT;

    //TODO maybe move to separate class for constant values which might be changed in future by DAO voting?
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
                    MIN_TRADE_AMOUNT = Coin.valueOf(10_000); // 0.25 EUR cent @ 2500 EUR/BTC 
                    break;
                case "LTC":
                    MIN_TRADE_AMOUNT = Coin.valueOf(625_000); // 0.25 EUR cent @ 40 EUR/LTC  
                    break;
                case "DOGE":
                    MIN_TRADE_AMOUNT = Coin.valueOf(8_000_000_000L);// 0.24 USD at DOGE price 0.003 USD;
                    break;
            }
        return MIN_TRADE_AMOUNT;
    }

    public static Coin getMaxBuyerSecurityDeposit() {
        if (MAX_BUYER_SECURITY_DEPOSIT == null)
            MAX_BUYER_SECURITY_DEPOSIT = getMinTradeAmount().multiply(2000); // about 500 EUR
        return MAX_BUYER_SECURITY_DEPOSIT;
    }

    public static Coin getMinBuyerSecurityDeposit() {
        if (MIN_BUYER_SECURITY_DEPOSIT == null)
            MIN_BUYER_SECURITY_DEPOSIT = getMinTradeAmount().multiply(10); // about 2.5 eur
        return MIN_BUYER_SECURITY_DEPOSIT;
    }

    public static Coin getDefaultBuyerSecurityDeposit() {
        if (DEFAULT_BUYER_SECURITY_DEPOSIT == null)
            DEFAULT_BUYER_SECURITY_DEPOSIT = getMinTradeAmount().multiply(300); // about 75 eur
        return DEFAULT_BUYER_SECURITY_DEPOSIT;
    }

    public static Coin getSellerSecurityDeposit() {
        if (SELLER_SECURITY_DEPOSIT == null)
            SELLER_SECURITY_DEPOSIT = getMinTradeAmount().multiply(100); // about 25 eur
        return SELLER_SECURITY_DEPOSIT;
    }
}
