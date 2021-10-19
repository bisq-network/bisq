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

package bisq.desktop.main.offer.bisq_v1;

import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;
import bisq.core.monetary.Volume;
import bisq.core.offer.OfferUtil;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;

import java.util.Optional;

// Shared utils for ViewModels
public class OfferViewModelUtil {
    public static String getTradeFeeWithFiatEquivalent(OfferUtil offerUtil,
                                                       Coin tradeFee,
                                                       boolean isCurrencyForMakerFeeBtc,
                                                       CoinFormatter formatter) {
        if (!isCurrencyForMakerFeeBtc && !DevEnv.isDaoActivated()) {
            return "";
        }

        Optional<Volume> optionalBtcFeeInFiat = offerUtil.getFeeInUserFiatCurrency(tradeFee,
                isCurrencyForMakerFeeBtc,
                formatter);

        return DisplayUtils.getFeeWithFiatAmount(tradeFee, optionalBtcFeeInFiat, formatter);
    }

    public static String getTradeFeeWithFiatEquivalentAndPercentage(OfferUtil offerUtil,
                                                                    Coin tradeFee,
                                                                    Coin tradeAmount,
                                                                    boolean isCurrencyForMakerFeeBtc,
                                                                    CoinFormatter formatter,
                                                                    Coin minTradeFee) {
        if (isCurrencyForMakerFeeBtc) {
            String feeAsBtc = formatter.formatCoinWithCode(tradeFee);
            String percentage;
            if (!tradeFee.isGreaterThan(minTradeFee)) {
                percentage = Res.get("guiUtil.requiredMinimum")
                        .replace("(", "")
                        .replace(")", "");
            } else {
                percentage = GUIUtil.getPercentage(tradeFee, tradeAmount) +
                        " " + Res.get("guiUtil.ofTradeAmount");
            }
            return offerUtil.getFeeInUserFiatCurrency(tradeFee,
                    isCurrencyForMakerFeeBtc,
                    formatter)
                    .map(VolumeUtil::formatAverageVolumeWithCode)
                    .map(feeInFiat -> Res.get("feeOptionWindow.btcFeeWithFiatAndPercentage", feeAsBtc, feeInFiat, percentage))
                    .orElseGet(() -> Res.get("feeOptionWindow.btcFeeWithPercentage", feeAsBtc, percentage));
        } else {
            // For BSQ we use the fiat equivalent only. Calculating the % value would be more effort.
            // We could calculate the BTC value if the BSQ fee and use that...
            return OfferViewModelUtil.getTradeFeeWithFiatEquivalent(offerUtil,
                    tradeFee,
                    false,
                    formatter);
        }
    }
}
