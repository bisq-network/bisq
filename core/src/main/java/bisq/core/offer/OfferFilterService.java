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

package bisq.core.offer;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.common.app.DevEnv;
import bisq.common.app.Version;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.collections.SetChangeListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class OfferFilterService {
    private final User user;
    private final Preferences preferences;
    private final FilterManager filterManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final Map<String, Boolean> insufficientCounterpartyTradeLimitCache = new HashMap<>();
    private final Map<String, Boolean> myInsufficientTradeLimitCache = new HashMap<>();

    @Inject
    public OfferFilterService(User user,
                              Preferences preferences,
                              FilterManager filterManager,
                              AccountAgeWitnessService accountAgeWitnessService) {
        this.user = user;
        this.preferences = preferences;
        this.filterManager = filterManager;
        this.accountAgeWitnessService = accountAgeWitnessService;

        if (user != null) {
            // If our accounts have changed we reset our myInsufficientTradeLimitCache as it depends on account data
            user.getPaymentAccountsAsObservable().addListener((SetChangeListener<PaymentAccount>) c ->
                    myInsufficientTradeLimitCache.clear());
        }
    }

    public enum Result {
        VALID(true),
        API_DISABLED,
        HAS_NO_PAYMENT_ACCOUNT_VALID_FOR_OFFER,
        HAS_NOT_SAME_PROTOCOL_VERSION,
        IS_IGNORED,
        IS_OFFER_BANNED,
        IS_CURRENCY_BANNED,
        IS_PAYMENT_METHOD_BANNED,
        IS_NODE_ADDRESS_BANNED,
        REQUIRE_UPDATE_TO_NEW_VERSION,
        IS_INSUFFICIENT_COUNTERPARTY_TRADE_LIMIT,
        IS_MY_INSUFFICIENT_TRADE_LIMIT,
        HIDE_BSQ_SWAPS_DUE_DAO_DEACTIVATED;

        @Getter
        private final boolean isValid;

        Result(boolean isValid) {
            this.isValid = isValid;
        }

        Result() {
            this(false);
        }
    }

    public Result canTakeOffer(Offer offer, boolean isTakerApiUser) {
        if (isTakerApiUser && filterManager.getFilter() != null && filterManager.getFilter().isDisableApi()) {
            return Result.API_DISABLED;
        }
        if (!isAnyPaymentAccountValidForOffer(offer)) {
            return Result.HAS_NO_PAYMENT_ACCOUNT_VALID_FOR_OFFER;
        }
        if (!hasSameProtocolVersion(offer)) {
            return Result.HAS_NOT_SAME_PROTOCOL_VERSION;
        }
        if (isIgnored(offer)) {
            return Result.IS_IGNORED;
        }
        if (isOfferBanned(offer)) {
            return Result.IS_OFFER_BANNED;
        }
        if (isCurrencyBanned(offer)) {
            return Result.IS_CURRENCY_BANNED;
        }
        if (isPaymentMethodBanned(offer)) {
            return Result.IS_PAYMENT_METHOD_BANNED;
        }
        if (isNodeAddressBanned(offer)) {
            return Result.IS_NODE_ADDRESS_BANNED;
        }
        if (requireUpdateToNewVersion()) {
            return Result.REQUIRE_UPDATE_TO_NEW_VERSION;
        }
        if (isInsufficientCounterpartyTradeLimit(offer)) {
            return Result.IS_INSUFFICIENT_COUNTERPARTY_TRADE_LIMIT;
        }
        if (isMyInsufficientTradeLimit(offer)) {
            return Result.IS_MY_INSUFFICIENT_TRADE_LIMIT;
        }
        if (!DevEnv.isDaoActivated() && offer.isBsqSwapOffer()) {
            return Result.HIDE_BSQ_SWAPS_DUE_DAO_DEACTIVATED;
        }

        return Result.VALID;
    }

    public boolean isAnyPaymentAccountValidForOffer(Offer offer) {
        return user.getPaymentAccounts() != null &&
                PaymentAccountUtil.isAnyPaymentAccountValidForOffer(offer, user.getPaymentAccounts());
    }

    public boolean hasSameProtocolVersion(Offer offer) {
        return offer.getProtocolVersion() == Version.TRADE_PROTOCOL_VERSION;
    }

    public boolean isIgnored(Offer offer) {
        return preferences.getIgnoreTradersList().stream()
                .anyMatch(i -> i.equals(offer.getMakerNodeAddress().getFullAddress()));
    }

    public boolean isOfferBanned(Offer offer) {
        return filterManager.isOfferIdBanned(offer.getId());
    }

    public boolean isCurrencyBanned(Offer offer) {
        return filterManager.isCurrencyBanned(offer.getCurrencyCode());
    }

    public boolean isPaymentMethodBanned(Offer offer) {
        return filterManager.isPaymentMethodBanned(offer.getPaymentMethod());
    }

    public boolean isNodeAddressBanned(Offer offer) {
        return filterManager.isNodeAddressBanned(offer.getMakerNodeAddress());
    }

    public boolean requireUpdateToNewVersion() {
        return filterManager.requireUpdateToNewVersionForTrading();
    }

    // This call is a bit expensive so we cache results
    public boolean isInsufficientCounterpartyTradeLimit(Offer offer) {
        String offerId = offer.getId();
        if (insufficientCounterpartyTradeLimitCache.containsKey(offerId)) {
            return insufficientCounterpartyTradeLimitCache.get(offerId);
        }

        boolean result = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) &&
                !accountAgeWitnessService.verifyPeersTradeAmount(offer, offer.getAmount(),
                        errorMessage -> {
                        });
        insufficientCounterpartyTradeLimitCache.put(offerId, result);
        return result;
    }

    // This call is a bit expensive so we cache results
    public boolean isMyInsufficientTradeLimit(Offer offer) {
        String offerId = offer.getId();
        if (myInsufficientTradeLimitCache.containsKey(offerId)) {
            return myInsufficientTradeLimitCache.get(offerId);
        }

        Optional<PaymentAccount> accountOptional = PaymentAccountUtil.getMostMaturePaymentAccountForOffer(offer,
                user.getPaymentAccounts(),
                accountAgeWitnessService);
        long myTradeLimit = accountOptional
                .map(paymentAccount -> accountAgeWitnessService.getMyTradeLimit(paymentAccount,
                        offer.getCurrencyCode(), offer.getMirroredDirection()))
                .orElse(0L);
        long offerMinAmount = offer.getMinAmount().value;
        log.debug("isInsufficientTradeLimit accountOptional={}, myTradeLimit={}, offerMinAmount={}, ",
                accountOptional.isPresent() ? accountOptional.get().getAccountName() : "null",
                Coin.valueOf(myTradeLimit).toFriendlyString(),
                Coin.valueOf(offerMinAmount).toFriendlyString());
        boolean result = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) &&
                accountOptional.isPresent() &&
                myTradeLimit < offerMinAmount;
        myInsufficientTradeLimitCache.put(offerId, result);
        return result;
    }
}
