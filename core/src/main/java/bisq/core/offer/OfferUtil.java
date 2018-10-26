/*
 * This file is part of Bisq.
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

package bisq.core.offer;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;
import bisq.core.util.CoinUtil;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This class holds utility methods for the creation of an Offer.
 * Most of these are extracted here because they are used both in the GUI and in the API.
 * <p>
 * Long-term there could be a GUI-agnostic OfferService which provides these and other functionalities to both the
 * GUI and the API.
 */
@Slf4j
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
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getMakerFeePerBtc(isCurrencyForMakerFeeBtc), amount);
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

    public static Volume getRoundedFiatVolume(Volume volumeByAmount) {
        // We want to get rounded to 1 unit of the fiat currency, e.g. 1 EUR.
        return getAdjustedFiatVolume(volumeByAmount, 1);
    }

    public static Volume getAdjustedVolumeForHalCash(Volume volumeByAmount) {
        // EUR has precision 4 and we want multiple of 10 so we divide by 100000 then
        // round and multiply with 10
        return getAdjustedFiatVolume(volumeByAmount, 10);
    }

    /**
     *
     * @param volumeByAmount      The volume generated from an amount
     * @param factor              The factor used for rounding. E.g. 1 means rounded to units of 1 EUR, 10 means rounded to 10 EUR...
     * @return The adjusted Fiat volume
     */
    @VisibleForTesting
    static Volume getAdjustedFiatVolume(Volume volumeByAmount, int factor) {
        // Fiat currencies use precision 4 and we want multiple of factor so we divide by 10000 * factor then
        // round and multiply with factor
        long roundedVolume = Math.round((double) volumeByAmount.getValue() / (10000d * factor)) * factor;
        // Smallest allowed volume is factor (e.g. 10 EUR or 1 EUR,...)
        roundedVolume = Math.max(factor, roundedVolume);
        return Volume.parse(String.valueOf(roundedVolume), volumeByAmount.getCurrencyCode());
    }

    /**
     * Calculate the possibly adjusted amount for {@code amount}, taking into account the
     * {@code price} and {@code maxTradeLimit} and {@code factor}.
     *
     * @param amount            Bitcoin amount which is a candidate for getting rounded.
     * @param price             Price used in relation ot that amount.
     * @param maxTradeLimit     The max. trade limit of the users account, in satoshis.
     * @return The adjusted amount
     */
    public static Coin getRoundedFiatAmount(Coin amount, Price price, long maxTradeLimit) {
        return getAdjustedAmount(amount, price, maxTradeLimit, 1);
    }

    public static Coin getAdjustedAmountForHalCash(Coin amount, Price price, long maxTradeLimit) {
        return getAdjustedAmount(amount, price, maxTradeLimit, 10);
    }

    /**
     * Calculate the possibly adjusted amount for {@code amount}, taking into account the
     * {@code price} and {@code maxTradeLimit} and {@code factor}.
     *
     * @param amount            Bitcoin amount which is a candidate for getting rounded.
     * @param price             Price used in relation ot that amount.
     * @param maxTradeLimit     The max. trade limit of the users account, in satoshis.
     * @param factor            The factor used for rounding. E.g. 1 means rounded to units of
     *                          1 EUR, 10 means rounded to 10 EUR, etc.
     * @return The adjusted amount
     */
    @VisibleForTesting
    static Coin getAdjustedAmount(Coin amount, Price price, long maxTradeLimit, int factor) {
        checkArgument(
                amount.getValue() >= 10_000,
                "amount needs to be above minimum of 10k satoshi"
        );
        checkArgument(
                factor > 0,
                "factor needs to be positive"
        );
        // Amount must result in a volume of min factor units of the fiat currency, e.g. 1 EUR or
        // 10 EUR in case of HalCash.
        Volume smallestUnitForVolume = Volume.parse(String.valueOf(factor), price.getCurrencyCode());
        if (smallestUnitForVolume.getValue() <= 0)
            return Coin.ZERO;

        Coin smallestUnitForAmount = price.getAmountByVolume(smallestUnitForVolume);
        long minTradeAmount = Restrictions.getMinTradeAmount().value;

        // We use 10 000 satoshi as min allowed amount
        checkArgument(
                minTradeAmount >= 10_000,
                "MinTradeAmount must be at least 10k satoshi"
        );
        smallestUnitForAmount = Coin.valueOf(Math.max(minTradeAmount, smallestUnitForAmount.value));
        // We don't allow smaller amount values than smallestUnitForAmount
        if (amount.compareTo(smallestUnitForAmount) < 0)
            amount = smallestUnitForAmount;

        // We get the adjusted volume from our amount
        Volume volume = getAdjustedFiatVolume(price.getVolumeByAmount(amount), factor);
        if (volume.getValue() <= 0)
            return Coin.ZERO;

        // From that adjusted volume we calculate back the amount. It might be a bit different as
        // the amount used as input before due rounding.
        amount = price.getAmountByVolume(volume);

        // For the amount we allow only 4 decimal places
        long adjustedAmount = Math.round((double) amount.value / 10000d) * 10000;

        // If we are above our trade limit we reduce the amount by the smallestUnitForAmount
        while (adjustedAmount > maxTradeLimit) {
            adjustedAmount -= smallestUnitForAmount.value;
        }
        adjustedAmount = Math.max(minTradeAmount, adjustedAmount);
        adjustedAmount = Math.min(maxTradeLimit, adjustedAmount);
        return Coin.valueOf(adjustedAmount);
    }

    @Nullable
    public static Volume getFeeInUserFiatCurrency(Coin makerFee, boolean isCurrencyForMakerFeeBtc,
                                                  Preferences preferences, PriceFeedService priceFeedService,
                                                  BsqFormatter bsqFormatter) {
        // We use the users currency derived from his selected country.
        // We don't use the preferredTradeCurrency from preferences as that can be also set to an altcoin.
        String countryCode = preferences.getUserCountry().code;
        String userCurrencyCode = CurrencyUtil.getCurrencyByCountryCode(countryCode).getCode();
        MarketPrice marketPrice = priceFeedService.getMarketPrice(userCurrencyCode);
        if (marketPrice != null && makerFee != null) {
            long marketPriceAsLong = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(marketPrice.getPrice(), Fiat.SMALLEST_UNIT_EXPONENT));
            Price userCurrencyPrice = Price.valueOf(userCurrencyCode, marketPriceAsLong);

            if (isCurrencyForMakerFeeBtc) {
                return userCurrencyPrice.getVolumeByAmount(makerFee);
            } else {
                MarketPrice bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");
                if (bsqMarketPrice != null) {
                    long bsqPriceAsLong = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(bsqMarketPrice.getPrice(), Altcoin.SMALLEST_UNIT_EXPONENT));
                    Price bsqPrice = Price.valueOf("BSQ", bsqPriceAsLong);
                    String inputValue = bsqFormatter.formatCoin(makerFee);
                    Volume makerFeeAsVolume = Volume.parse(inputValue, "BSQ");
                    Coin requiredBtc = bsqPrice.getAmountByVolume(makerFeeAsVolume);
                    return userCurrencyPrice.getVolumeByAmount(requiredBtc);
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    public static String getFeeWithFiatAmount(Coin makerFeeAsCoin, Volume feeInFiat, BSFormatter btcFormatter) {
        String fee = makerFeeAsCoin != null ? btcFormatter.formatCoinWithCode(makerFeeAsCoin) : Res.get("shared.na");
        String feeInFiatAsString = feeInFiat != null ? btcFormatter.formatVolumeWithCode(feeInFiat) : Res.get("shared.na");
        return Res.get("feeOptionWindow.fee", fee, feeInFiatAsString);
    }
}
