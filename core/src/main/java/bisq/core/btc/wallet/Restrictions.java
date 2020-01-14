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
    private static Coin MIN_BUYER_SECURITY_DEPOSIT;
    // For the seller we use a fixed one as there is no way the seller can cancel the trade
    // To make it editable would just increase complexity.
    private static Coin SELLER_SECURITY_DEPOSIT;
    // At mediation we require a min. payout to the losing party to keep incentive for the trader to accept the
    // mediated payout. For Refund agent cases we do not have that restriction.
    private static Coin MIN_REFUND_AT_MEDIATED_DISPUTE;

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
            MIN_TRADE_AMOUNT = Coin.valueOf(10_000); // 0,7 USD @ 7000 USD/BTC
        return MIN_TRADE_AMOUNT;
    }

    public static double getDefaultBuyerSecurityDepositAsPercent() {
        return 0.15; // 15% of trade amount.
    }

    public static double getMinBuyerSecurityDepositAsPercent() {
        return 0.15; // 15% of trade amount.
    }

    public static double getMaxBuyerSecurityDepositAsPercent() {
        return 0.5; // 50% of trade amount. For a 1 BTC trade it is about 3500 USD @ 7000 USD/BTC
    }

    // We use MIN_BUYER_SECURITY_DEPOSIT as well as lower bound in case of small trade amounts.
    // So 0.0005 BTC is the min. buyer security deposit even with amount of 0.0001 BTC and 0.05% percentage value.
    public static Coin getMinBuyerSecurityDepositAsCoin() {
        if (MIN_BUYER_SECURITY_DEPOSIT == null)
            MIN_BUYER_SECURITY_DEPOSIT = Coin.parseCoin("0.006"); // 0.006 BTC about 42 USD @ 7000 USD/BTC
        return MIN_BUYER_SECURITY_DEPOSIT;
    }


    public static double getSellerSecurityDepositAsPercent() {
        return 0.15; // 15% of trade amount.
    }

    public static Coin getMinSellerSecurityDepositAsCoin() {
        if (SELLER_SECURITY_DEPOSIT == null)
            SELLER_SECURITY_DEPOSIT = Coin.parseCoin("0.006"); // 0.006 BTC about 42 USD @ 7000 USD/BTC
        return SELLER_SECURITY_DEPOSIT;
    }

    // This value must not be lower than MIN_BUYER_SECURITY_DEPOSIT or SELLER_SECURITY_DEPOSIT
    public static Coin getMinRefundAtMediatedDispute() {
        if (MIN_REFUND_AT_MEDIATED_DISPUTE == null)
            MIN_REFUND_AT_MEDIATED_DISPUTE = Coin.parseCoin("0.003"); // 0.003 BTC about 21 USD @ 7000 USD/BTC
        return MIN_REFUND_AT_MEDIATED_DISPUTE;
    }
}
