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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.payment.F2FAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.AutoConfirmSettings;
import bisq.core.user.Preferences;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.app.Capabilities;
import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class holds utility methods for the creation of an Offer.
 * Most of these are extracted here because they are used both in the GUI and in the API.
 * <p>
 * Long-term there could be a GUI-agnostic OfferService which provides these and other functionality to both the
 * GUI and the API.
 */
@Slf4j
@Singleton
public class OfferUtil {

    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BsqWalletService bsqWalletService;
    private final FilterManager filterManager;
    private final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final P2PService p2PService;
    private final ReferralIdService referralIdService;

    @Inject
    public OfferUtil(AccountAgeWitnessService accountAgeWitnessService,
                     BsqWalletService bsqWalletService,
                     FilterManager filterManager,
                     Preferences preferences,
                     PriceFeedService priceFeedService,
                     P2PService p2PService,
                     ReferralIdService referralIdService) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.bsqWalletService = bsqWalletService;
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.p2PService = p2PService;
        this.referralIdService = referralIdService;
    }

    /**
     * Given the direction, is this a BUY?
     *
     * @param direction the offer direction
     * @return {@code true} for an offer to buy BTC from the taker, {@code false} for an offer to sell BTC to the taker
     */
    public boolean isBuyOffer(OfferPayload.Direction direction) {
        return direction == OfferPayload.Direction.BUY;
    }

    /**
     * Returns the makerFee as Coin, this can be priced in BTC or BSQ.
     *
     * @param amount           the amount of BTC to trade
     * @return the maker fee for the given trade amount, or {@code null} if the amount is {@code null}
     */
    @Nullable
    public Coin getMakerFee(@Nullable Coin amount) {
        boolean isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc(amount);
        return CoinUtil.getMakerFee(isCurrencyForMakerFeeBtc, amount);
    }

    /**
     * Checks if the maker fee should be paid in BTC, this can be the case due to user preference or because the user
     * doesn't have enough BSQ.
     *
     * @param amount           the amount of BTC to trade
     * @return {@code true} if BTC is preferred or the trade amount is nonnull and there isn't enough BSQ for it
     */
    public boolean isCurrencyForMakerFeeBtc(@Nullable Coin amount) {
        boolean payFeeInBtc = preferences.getPayFeeInBtc();
        boolean bsqForFeeAvailable = isBsqForMakerFeeAvailable(amount);
        return payFeeInBtc || !bsqForFeeAvailable;
    }

    /**
     * Checks if the available BSQ balance is sufficient to pay for the offer's maker fee.
     *
     * @param amount           the amount of BTC to trade
     * @return {@code true} if the balance is sufficient, {@code false} otherwise
     */
    public boolean isBsqForMakerFeeAvailable(@Nullable Coin amount) {
        Coin availableBalance = bsqWalletService.getAvailableConfirmedBalance();
        Coin makerFee = CoinUtil.getMakerFee(false, amount);

        // If we don't know yet the maker fee (amount is not set) we return true, otherwise we would disable BSQ
        // fee each time we open the create offer screen as there the amount is not set.
        if (makerFee == null)
            return true;

        Coin surplusFunds = availableBalance.subtract(makerFee);
        if (Restrictions.isDust(surplusFunds)) {
            return false; // we can't be left with dust
        }
        return !availableBalance.subtract(makerFee).isNegative();
    }


    @Nullable
    public Coin getTakerFee(boolean isCurrencyForTakerFeeBtc, @Nullable Coin amount) {
        if (amount != null) {
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
            return CoinUtil.maxCoin(feePerBtc, FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc));
        } else {
            return null;
        }
    }

    public boolean isCurrencyForTakerFeeBtc(Coin amount) {
        boolean payFeeInBtc = preferences.getPayFeeInBtc();
        boolean bsqForFeeAvailable = isBsqForTakerFeeAvailable(amount);
        return payFeeInBtc || !bsqForFeeAvailable;
    }

    public boolean isBsqForTakerFeeAvailable(@Nullable Coin amount) {
        Coin availableBalance = bsqWalletService.getAvailableConfirmedBalance();
        Coin takerFee = getTakerFee(false, amount);

        // If we don't know yet the maker fee (amount is not set) we return true, otherwise we would disable BSQ
        // fee each time we open the create offer screen as there the amount is not set.
        if (takerFee == null)
            return true;

        Coin surplusFunds = availableBalance.subtract(takerFee);
        if (Restrictions.isDust(surplusFunds)) {
            return false; // we can't be left with dust
        }
        return !availableBalance.subtract(takerFee).isNegative();
    }

    public Optional<Volume> getFeeInUserFiatCurrency(Coin makerFee, boolean isCurrencyForMakerFeeBtc,
                                                            CoinFormatter bsqFormatter) {
        String countryCode = preferences.getUserCountry().code;
        String userCurrencyCode = CurrencyUtil.getCurrencyByCountryCode(countryCode).getCode();
        return getFeeInUserFiatCurrency(makerFee,
                isCurrencyForMakerFeeBtc,
                userCurrencyCode,
                bsqFormatter);
    }

    private Optional<Volume> getFeeInUserFiatCurrency(Coin makerFee, boolean isCurrencyForMakerFeeBtc,
                                                             String userCurrencyCode,
                                                             CoinFormatter bsqFormatter) {
        // We use the users currency derived from his selected country.
        // We don't use the preferredTradeCurrency from preferences as that can be also set to an altcoin.

        MarketPrice marketPrice = priceFeedService.getMarketPrice(userCurrencyCode);
        if (marketPrice != null && makerFee != null) {
            long marketPriceAsLong = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(marketPrice.getPrice(), Fiat.SMALLEST_UNIT_EXPONENT));
            Price userCurrencyPrice = Price.valueOf(userCurrencyCode, marketPriceAsLong);

            if (isCurrencyForMakerFeeBtc) {
                return Optional.of(userCurrencyPrice.getVolumeByAmount(makerFee));
            } else {
                Optional<Price> optionalBsqPrice = priceFeedService.getBsqPrice();
                if (optionalBsqPrice.isPresent()) {
                    Price bsqPrice = optionalBsqPrice.get();
                    String inputValue = bsqFormatter.formatCoin(makerFee);
                    Volume makerFeeAsVolume = Volume.parse(inputValue, "BSQ");
                    Coin requiredBtc = bsqPrice.getAmountByVolume(makerFeeAsVolume);
                    return Optional.of(userCurrencyPrice.getVolumeByAmount(requiredBtc));
                } else {
                    return Optional.empty();
                }
            }
        } else {
            return Optional.empty();
        }
    }


    public Map<String, String> getExtraDataMap(PaymentAccount paymentAccount,
                                                      String currencyCode,
                                                      OfferPayload.Direction direction) {
        Map<String, String> extraDataMap = new HashMap<>();
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            String myWitnessHashAsHex = accountAgeWitnessService.getMyWitnessHashAsHex(paymentAccount.getPaymentAccountPayload());
            extraDataMap.put(OfferPayload.ACCOUNT_AGE_WITNESS_HASH, myWitnessHashAsHex);
        }

        if (referralIdService.getOptionalReferralId().isPresent()) {
            extraDataMap.put(OfferPayload.REFERRAL_ID, referralIdService.getOptionalReferralId().get());
        }

        if (paymentAccount instanceof F2FAccount) {
            extraDataMap.put(OfferPayload.F2F_CITY, ((F2FAccount) paymentAccount).getCity());
            extraDataMap.put(OfferPayload.F2F_EXTRA_INFO, ((F2FAccount) paymentAccount).getExtraInfo());
        }

        extraDataMap.put(OfferPayload.CAPABILITIES, Capabilities.app.toStringList());

        if (currencyCode.equals("XMR") && direction == OfferPayload.Direction.SELL) {
            preferences.getAutoConfirmSettingsList().stream()
                    .filter(e -> e.getCurrencyCode().equals("XMR"))
                    .filter(AutoConfirmSettings::isEnabled)
                    .forEach(e -> extraDataMap.put(OfferPayload.XMR_AUTO_CONF, OfferPayload.XMR_AUTO_CONF_ENABLED_VALUE));
        }

        return extraDataMap.isEmpty() ? null : extraDataMap;
    }

    public void validateOfferData(double buyerSecurityDeposit,
                                         PaymentAccount paymentAccount,
                                         String currencyCode,
                                         Coin makerFeeAsCoin) {
        checkNotNull(makerFeeAsCoin, "makerFee must not be null");
        checkNotNull(p2PService.getAddress(), "Address must not be null");
        checkArgument(buyerSecurityDeposit <= Restrictions.getMaxBuyerSecurityDepositAsPercent(),
                "securityDeposit must not exceed " +
                        Restrictions.getMaxBuyerSecurityDepositAsPercent());
        checkArgument(buyerSecurityDeposit >= Restrictions.getMinBuyerSecurityDepositAsPercent(),
                "securityDeposit must not be less than " +
                        Restrictions.getMinBuyerSecurityDepositAsPercent());
        checkArgument(!filterManager.isCurrencyBanned(currencyCode),
                Res.get("offerbook.warning.currencyBanned"));
        checkArgument(!filterManager.isPaymentMethodBanned(paymentAccount.getPaymentMethod()),
                Res.get("offerbook.warning.paymentMethodBanned"));
    }

    // TODO no code duplication found in UI code (added for API)
   /* public static Coin getFundsNeededForOffer(Coin tradeAmount, Coin buyerSecurityDeposit, OfferPayload.Direction direction) {
        boolean buyOffer = isBuyOffer(direction);
        Coin needed = buyOffer ? buyerSecurityDeposit : Restrictions.getSellerSecurityDeposit();
        if (!buyOffer)
            needed = needed.add(tradeAmount);

        return needed;
    }*/
}
