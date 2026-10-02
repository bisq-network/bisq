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
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.availability.AvailabilityResult;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.common.app.Version;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.collections.SetChangeListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Singleton
public class OfferFilterService {
    private final User user;
    private final Preferences preferences;
    private final FilterPolicyService filterPolicyService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final Map<String, Boolean> insufficientCounterpartyTradeLimitCache = new HashMap<>();
    private final Map<String, Boolean> myInsufficientTradeLimitCache = new HashMap<>();
    // Makers whose last answer to one of our availability requests was USER_IGNORED, by node
    // address, with the time of that answer. A maker's ignore list is private to the maker, so
    // this answer is the only way to know that all offers of the maker are closed to us. Entries
    // expire, so a maker who removed us from the list is asked again; an AVAILABLE answer
    // removes the entry at once.
    private final Map<String, Long> makersIgnoringUs = new HashMap<>();
    public static final long MAKER_IGNORES_US_TTL_MS = TimeUnit.HOURS.toMillis(1);

    @Inject
    public OfferFilterService(User user,
                              Preferences preferences,
                              FilterPolicyService filterPolicyService,
                              AccountAgeWitnessService accountAgeWitnessService) {
        this.user = user;
        this.preferences = preferences;
        this.filterPolicyService = filterPolicyService;
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
        IS_IGNORED_BY_MAKER,
        IS_OFFER_BANNED,
        IS_CURRENCY_BANNED,
        IS_PAYMENT_METHOD_BANNED,
        IS_NODE_ADDRESS_BANNED,
        REQUIRE_UPDATE_TO_NEW_VERSION,
        IS_INSUFFICIENT_COUNTERPARTY_TRADE_LIMIT,
        IS_MY_INSUFFICIENT_TRADE_LIMIT,
        IS_BSQ_SWAP_DISABLED,
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
        if (isTakerApiUser && filterPolicyService.isApiDisabled()) {
            return Result.API_DISABLED;
        }
        if (isBsqSwapDisabled(offer)) {
            return Result.IS_BSQ_SWAP_DISABLED;
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
        if (isIgnoredByMaker(offer)) {
            return Result.IS_IGNORED_BY_MAKER;
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

    public boolean isIgnoredByMaker(Offer offer) {
        String maker = offer.getMakerNodeAddress().getFullAddress();
        Long since = makersIgnoringUs.get(maker);
        if (since == null)
            return false;
        if (now() - since > MAKER_IGNORES_US_TTL_MS) {
            makersIgnoringUs.remove(maker);
            return false;
        }
        return true;
    }

    // The maker's answer to one of our availability requests. Only USER_IGNORED closes the
    // maker and only AVAILABLE reopens them; every other answer is about that one request.
    public void onAvailabilityAnswer(Offer offer, @Nullable AvailabilityResult result) {
        if (result == null)
            return;
        String maker = offer.getMakerNodeAddress().getFullAddress();
        if (result == AvailabilityResult.USER_IGNORED)
            makersIgnoringUs.put(maker, now());
        else if (result == AvailabilityResult.AVAILABLE)
            makersIgnoringUs.remove(maker);
    }

    @VisibleForTesting
    protected long now() {
        return System.currentTimeMillis();
    }

    public boolean isOfferBanned(Offer offer) {
        return filterPolicyService.isOfferIdBanned(offer.getId());
    }

    public boolean isCurrencyBanned(Offer offer) {
        return filterPolicyService.isCurrencyBanned(offer.getCurrencyCode());
    }

    public boolean isPaymentMethodBanned(Offer offer) {
        return filterPolicyService.isPaymentMethodBanned(offer.getPaymentMethod());
    }

    public boolean isNodeAddressBanned(Offer offer) {
        return filterPolicyService.isNodeAddressBanned(offer.getMakerNodeAddress());
    }

    public boolean isBsqSwapDisabled(Offer offer) {
        return offer.isBsqSwapOffer() && filterPolicyService.isBsqSwapDisabled();
    }

    public boolean requireUpdateToNewVersion() {
        return filterPolicyService.requireUpdateToNewVersionForTrading();
    }

    // This call is a bit expensive so we cache results
    public boolean isInsufficientCounterpartyTradeLimit(Offer offer) {
        String offerId = offer.getId();
        if (insufficientCounterpartyTradeLimitCache.containsKey(offerId)) {
            return insufficientCounterpartyTradeLimitCache.get(offerId);
        }

        boolean result = offer.isFiatOffer() &&
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
        boolean result = accountOptional.isPresent() && myTradeLimit < offerMinAmount;
        myInsufficientTradeLimitCache.put(offerId, result);
        return result;
    }

    public void resetTradeLimitCache() {
        myInsufficientTradeLimitCache.clear();
    }
}
