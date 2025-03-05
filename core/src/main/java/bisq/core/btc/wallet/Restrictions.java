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

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

public class Restrictions {
    private static final Coin MIN_TRADE_AMOUNT = Coin.valueOf(10_000);
    private static final Coin MIN_SECURITY_DEPOSIT = Coin.parseCoin("0.0003");
    private static final double MIN_SECURITY_DEPOSIT_AS_PERCENT = 0.15; // 15% of trade amount.
    private static final double MAX_BUYER_SECURITY_DEPOSIT_AS_PERCENT = 0.5; // 50% of trade amount.
    // At mediation, we require a min. payout to the losing party to keep incentive for the trader to accept the
    // mediated payout. For Refund agent cases we do not have that restriction.
    private static final Coin MIN_REFUND_AT_MEDIATED_DISPUTE = MIN_SECURITY_DEPOSIT.divide(2);
    private static Coin minNonDustOutput;

    public static Coin getMinNonDustOutput() {
        if (minNonDustOutput == null)
            minNonDustOutput = Config.baseCurrencyNetwork().getParameters().getMinNonDustOutput();
        return minNonDustOutput;
    }

    public static boolean isAboveDust(Coin amount) {
        return amount.compareTo(getMinNonDustOutput()) >= 0;
    }

    public static boolean isDust(Coin amount) {
        return !isAboveDust(amount);
    }

    public static Coin getMinTradeAmount() {
        return MIN_TRADE_AMOUNT;
    }

    public static double getDefaultBuyerSecurityDepositAsPercent() {
        return MIN_SECURITY_DEPOSIT_AS_PERCENT;
    }

    public static double getMinBuyerSecurityDepositAsPercent() {
        return MIN_SECURITY_DEPOSIT_AS_PERCENT;
    }

    public static double getMaxBuyerSecurityDepositAsPercent() {
        return MAX_BUYER_SECURITY_DEPOSIT_AS_PERCENT;
    }

    // We use MIN_BUYER_SECURITY_DEPOSIT as well as lower bound in case of small trade amounts.
    public static Coin getMinBuyerSecurityDepositAsCoin() {
        return MIN_SECURITY_DEPOSIT;
    }

    public static double getSellerSecurityDepositAsPercent() {
        return MIN_SECURITY_DEPOSIT_AS_PERCENT;
    }

    public static double getMinSellerSecurityDepositAsPercent() {
        return MIN_SECURITY_DEPOSIT_AS_PERCENT;
    }

    public static Coin getMinSellerSecurityDepositAsCoin() {
        return MIN_SECURITY_DEPOSIT;
    }

    // 5% or at least half of the deposit (0.00015 BTC) to keep incentive for trader to accept mediation result.
    public static Coin getMinRefundAtMediatedDispute(Coin tradeAmount) {
        Coin fivePercentOfTradeAmount = tradeAmount.div(20); // 5%
        if (fivePercentOfTradeAmount.isLessThan(MIN_REFUND_AT_MEDIATED_DISPUTE)) {
            return MIN_REFUND_AT_MEDIATED_DISPUTE;
        } else {
            return fivePercentOfTradeAmount;
        }
    }

    public static int getLockTime(boolean isAsset) {
        // 10 days for altcoins, 20 days for other payment methods
        return isAsset ? 144 * 10 : 144 * 20;
    }
}
