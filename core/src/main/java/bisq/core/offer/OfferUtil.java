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
import bisq.core.btc.Restrictions;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Country;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.BankAccount;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.F2FAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.SameBankAccount;
import bisq.core.payment.SepaAccount;
import bisq.core.payment.SepaInstantAccount;
import bisq.core.payment.SpecificBanksAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.Preferences;
import bisq.core.util.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

    public static ArrayList<String> getAcceptedCountryCodes(PaymentAccount paymentAccount) {
        ArrayList<String> acceptedCountryCodes = null;
        if (paymentAccount instanceof SepaAccount) {
            acceptedCountryCodes = new ArrayList<>(((SepaAccount) paymentAccount).getAcceptedCountryCodes());
        } else if (paymentAccount instanceof SepaInstantAccount) {
            acceptedCountryCodes = new ArrayList<>(((SepaInstantAccount) paymentAccount).getAcceptedCountryCodes());
        } else if (paymentAccount instanceof CountryBasedPaymentAccount) {
            acceptedCountryCodes = new ArrayList<>();
            Country country = ((CountryBasedPaymentAccount) paymentAccount).getCountry();
            if (country != null)
                acceptedCountryCodes.add(country.code);
        }
        return acceptedCountryCodes;
    }

    public static ArrayList<String> getAcceptedBanks(PaymentAccount paymentAccount) {
        ArrayList<String> acceptedBanks = null;
        if (paymentAccount instanceof SpecificBanksAccount) {
            acceptedBanks = new ArrayList<>(((SpecificBanksAccount) paymentAccount).getAcceptedBanks());
        } else if (paymentAccount instanceof SameBankAccount) {
            acceptedBanks = new ArrayList<>();
            acceptedBanks.add(((SameBankAccount) paymentAccount).getBankId());
        }
        return acceptedBanks;
    }

    public static String getBankId(PaymentAccount paymentAccount) {
        return paymentAccount instanceof BankAccount ? ((BankAccount) paymentAccount).getBankId() : null;
    }

    // That is optional and set to null if not supported (AltCoins, OKPay,...)
    public static String getCountryCode(PaymentAccount paymentAccount) {
        if (paymentAccount instanceof CountryBasedPaymentAccount) {
            Country country = ((CountryBasedPaymentAccount) paymentAccount).getCountry();
            return country != null ? country.code : null;
        } else {
            return null;
        }
    }

    public static long getMaxTradeLimit(AccountAgeWitnessService accountAgeWitnessService, PaymentAccount paymentAccount, String currencyCode) {
        if (paymentAccount != null)
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, currencyCode);
        else
            return 0;
    }

    public static long getMaxTradePeriod(PaymentAccount paymentAccount) {
        return paymentAccount.getPaymentMethod().getMaxTradePeriod();
    }

    public static Map<String, String> getExtraDataMap(AccountAgeWitnessService accountAgeWitnessService,
                                                      ReferralIdService referralIdService,
                                                      PaymentAccount paymentAccount,
                                                      String currencyCode) {
        Map<String, String> extraDataMap = null;
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            extraDataMap = new HashMap<>();
            final String myWitnessHashAsHex = accountAgeWitnessService.getMyWitnessHashAsHex(paymentAccount.getPaymentAccountPayload());
            extraDataMap.put(OfferPayload.ACCOUNT_AGE_WITNESS_HASH, myWitnessHashAsHex);
        }

        if (referralIdService.getOptionalReferralId().isPresent()) {
            if (extraDataMap == null)
                extraDataMap = new HashMap<>();
            extraDataMap.put(OfferPayload.REFERRAL_ID, referralIdService.getOptionalReferralId().get());
        }

        if (paymentAccount instanceof F2FAccount) {
            if (extraDataMap == null)
                extraDataMap = new HashMap<>();
            extraDataMap.put(OfferPayload.F2F_CITY, ((F2FAccount) paymentAccount).getCity());
            extraDataMap.put(OfferPayload.F2F_EXTRA_INFO, ((F2FAccount) paymentAccount).getExtraInfo());
        }

        return extraDataMap;
    }

    public static void validateOfferData(FilterManager filterManager,
                                         P2PService p2PService,
                                         Coin buyerSecurityDepositAsCoin,
                                         PaymentAccount paymentAccount,
                                         String currencyCode, Coin makerFeeAsCoin) {
        checkArgument(buyerSecurityDepositAsCoin.compareTo(Restrictions.getMaxBuyerSecurityDeposit()) <= 0,
                "securityDeposit must be not exceed " +
                        Restrictions.getMaxBuyerSecurityDeposit().toFriendlyString());
        checkArgument(buyerSecurityDepositAsCoin.compareTo(Restrictions.getMinBuyerSecurityDeposit()) >= 0,
                "securityDeposit must be not be less than " +
                        Restrictions.getMinBuyerSecurityDeposit().toFriendlyString());

        checkArgument(!filterManager.isCurrencyBanned(currencyCode),
                Res.get("offerbook.warning.currencyBanned"));
        checkArgument(!filterManager.isPaymentMethodBanned(paymentAccount.getPaymentMethod()),
                Res.get("offerbook.warning.paymentMethodBanned"));
        checkNotNull(makerFeeAsCoin, "makerFee must not be null");
        checkNotNull(p2PService.getAddress(), "Address must not be null");
    }
}
