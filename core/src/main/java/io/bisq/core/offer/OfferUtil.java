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

public class OfferUtil {
    @Nullable
    public static Coin getMakerFee(BsqWalletService bsqWalletService, Preferences preferences, Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        return getMakerFee(isCurrencyForMakerFeeBtc(preferences, bsqWalletService, amount, marketPriceAvailable, marketPriceMargin), amount,  marketPriceAvailable,  marketPriceMargin);
    }

    public static boolean isBuyOffer(OfferPayload.Direction direction) {
        return direction == OfferPayload.Direction.BUY;
    }

    @Nullable
    public static Coin getMakerFee(boolean isCurrencyForMakerFeeBtc, Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
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


    public static boolean isCurrencyForMakerFeeBtc(Preferences preferences, BsqWalletService bsqWalletService, Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        return preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(bsqWalletService, amount,  marketPriceAvailable,  marketPriceMargin);
    }

    public static boolean isBsqForFeeAvailable(BsqWalletService bsqWalletService, Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        return BisqEnvironment.isBaseCurrencySupportingBsq() &&
                getMakerFee(false, amount, marketPriceAvailable, marketPriceMargin) != null &&
                bsqWalletService.getAvailableBalance() != null &&
                getMakerFee(false , amount, marketPriceAvailable, marketPriceMargin) != null &&
                !bsqWalletService.getAvailableBalance().subtract(getMakerFee(false, amount, marketPriceAvailable, marketPriceMargin)).isNegative();
    }



}
