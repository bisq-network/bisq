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

package io.bisq.core.offer;

import io.bisq.common.util.MathUtils;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.user.Preferences;
import io.bisq.core.util.CoinUtil;
import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;

/**
 * This class holds utility methods for the creation of an Offer.
 * Most of these are extracted here because they are used both in the GUI and in the API.
 * <p>
 * Long-term there could be a GUI-agnostic OfferService which provides these and other functionalities to both the
 * GUI and the API.
 */
public class OfferUtil {

    /**
     * Given the direction, is this a BUY?
     *
     * @param direction
     * @return
     */
    public static boolean isBuyOffer(OfferPayload.Direction direction) {
        return direction == OfferPayload.Direction.BUY;
    }

    /**
     * Returns the makerFee as Coin, this can be priced in BTC or BSQ.
     *
     * @param bsqWalletService
     * @param preferences          preferences are used to see if the user indicated a preference for paying fees in BTC
     * @param amount
     * @param marketPriceAvailable
     * @param marketPriceMargin
     * @return
     */
    @Nullable
    public static Coin getMakerFee(BsqWalletService bsqWalletService, Preferences preferences, Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        final boolean isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc(preferences, bsqWalletService, amount, marketPriceAvailable, marketPriceMargin);
        return getMakerFee(isCurrencyForMakerFeeBtc,
                amount,
                marketPriceAvailable,
                marketPriceMargin);
    }

    /**
     * Calculates the maker fee for the given amount, marketPrice and marketPriceMargin.
     *
     * @param isCurrencyForMakerFeeBtc
     * @param amount
     * @param marketPriceAvailable
     * @param marketPriceMargin
     * @return
     */
    @Nullable
    public static Coin getMakerFee(boolean isCurrencyForMakerFeeBtc, @Nullable Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        if (amount != null) {
            final Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getMakerFeePerBtc(isCurrencyForMakerFeeBtc), amount);
            double makerFeeAsDouble = (double) feePerBtc.value;
            if (marketPriceAvailable) {
                if (marketPriceMargin > 0)
                    makerFeeAsDouble = makerFeeAsDouble * Math.sqrt(marketPriceMargin * 100);
                else
                    makerFeeAsDouble = 0;
                // For BTC we round so min value change is 100 satoshi
                if (isCurrencyForMakerFeeBtc)
                    makerFeeAsDouble = MathUtils.roundDouble(makerFeeAsDouble / 100, 0) * 100;
            }

            return CoinUtil.maxCoin(Coin.valueOf(MathUtils.doubleToLong(makerFeeAsDouble)), FeeService.getMinMakerFee(isCurrencyForMakerFeeBtc));
        } else {
            return null;
        }
    }


    /**
     * Checks if the maker fee should be paid in BTC, this can be the case due to user preference or because the user
     * doesn't have enough BSQ.
     *
     * @param preferences
     * @param bsqWalletService
     * @param amount
     * @param marketPriceAvailable
     * @param marketPriceMargin
     * @return
     */
    public static boolean isCurrencyForMakerFeeBtc(Preferences preferences, BsqWalletService bsqWalletService, Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        return preferences.getPayFeeInBtc() ||
                !isBsqForFeeAvailable(bsqWalletService, amount, marketPriceAvailable, marketPriceMargin);
    }

    /**
     * Checks if the available BSQ balance is sufficient to pay for the offer's maker fee.
     *
     * @param bsqWalletService
     * @param amount
     * @param marketPriceAvailable
     * @param marketPriceMargin
     * @return
     */
    public static boolean isBsqForFeeAvailable(BsqWalletService bsqWalletService, @Nullable Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        final Coin makerFee = getMakerFee(false, amount, marketPriceAvailable, marketPriceMargin);
        final Coin availableBalance = bsqWalletService.getAvailableBalance();
        return makerFee != null &&
                BisqEnvironment.isBaseCurrencySupportingBsq() &&
                availableBalance != null &&
                !availableBalance.subtract(makerFee).isNegative();
    }
}
