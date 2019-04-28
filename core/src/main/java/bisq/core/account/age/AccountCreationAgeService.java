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

package bisq.core.account.age;

import bisq.core.account.score.AccountScoreCategory;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.lang3.time.DateUtils;

import java.util.Date;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Responsible for delayed payout based on account age. It does not consider if the account was yet used for
 * trading and is therefor not considered a strong protection.
 */
@Slf4j
public class AccountCreationAgeService {
    private final static int MAX_REQUIRED_AGE = 42;
    private final static int MAX_DELAY = 28;
    private final AccountAgeWitnessService accountAgeWitnessService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountCreationAgeService(AccountAgeWitnessService accountAgeWitnessService) {
        this.accountAgeWitnessService = accountAgeWitnessService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the delay for the payout in days based on a linear function which starts with a delay of 28 days at age 0 and
     * ends with 0 days delay at account age 42 (6 weeks).
     * @param buyersAccountAge      Account age of buyer in ms
     * @param requiredAccountAge    Required account age in ms
     * @return The delay for the delayed payout in days.
     */
    @VisibleForTesting
    public static long getDelayInDays(long buyersAccountAge, long requiredAccountAge) {
        double maxDelay = (double) MAX_DELAY;
        double requiredAccountAgeAsDays = ((double) requiredAccountAge) / DateUtils.MILLIS_PER_DAY;
        double buyersAccountAgeAsDays = ((double) buyersAccountAge) / DateUtils.MILLIS_PER_DAY;
        double result = (requiredAccountAgeAsDays - buyersAccountAgeAsDays) / requiredAccountAgeAsDays * maxDelay;
        return Math.round(Math.max(0, result));
    }

    /**
     * @param trade     The trade for which we want to know the delayed payout for the buyer.
     * @return The delay in ms for the payout for the fiat buyer in a trade.
     */
    public long getDelay(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null) {
            return 0;
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return 0;
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return 0;
        }

        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        long requiredAccountAge = getRequiredAccountAge(offer.getPaymentMethod());

        return getDelayInDays(buyersAccountAge, requiredAccountAge) * DateUtils.MILLIS_PER_DAY;
    }

    /**
     * @param offer     The offer for which we want to know the delayed payout.
     * @return The delay in ms for the payout if offer maker is buyer.
     */
    public long getDelayForOffer(Offer offer) {
        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return 0;
        }
        if (offer.getDirection() == OfferPayload.Direction.SELL) {
            return 0;
        }

        long buyersAccountAge = accountAgeWitnessService.getMakersAccountAge(offer);
        long requiredAccountAge = getRequiredAccountAge(offer.getPaymentMethod());
        return getDelayInDays(buyersAccountAge, requiredAccountAge) * DateUtils.MILLIS_PER_DAY;
    }

    /**
     * Delay for maker if he is fiat buyer.
     * @param myPaymentAccount      My payment account used for my offer
     * @param currencyCode          Currency code of my offer
     * @param direction             Direction of my offer
     * @return The delay in ms for the payout of maker.
     */
    public long getDelayForMyOffer(PaymentAccount myPaymentAccount, @Nullable String currencyCode, @Nullable OfferPayload.Direction direction) {
        if (direction != null && direction == OfferPayload.Direction.SELL) {
            return 0;
        }

        if (currencyCode != null && CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return 0;
        }

        long myAccountAge = accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload());
        long requiredAccountAge = getRequiredAccountAge(myPaymentAccount.getPaymentMethod());
        return getDelayInDays(myAccountAge, requiredAccountAge) * DateUtils.MILLIS_PER_DAY;
    }

    /**
     * @param trade     The trade for which we want to know the delayed payout date.
     * @return The date of a delayed payout
     */
    public Date getDelayedTradePayoutDate(Trade trade) {
        long delay = getDelay(trade);
        long now = new Date().getTime();
        return new Date(delay + now);
    }

    /**
     * Depending on payment methods chargeback risk we determine the required account age when we do not apply a payout delay anymore.
     * The max. period is 42 days/6 weeks. For lower risk payment methods we reduce that to 21 days.
     * @param paymentMethod     The paymentMethod which determines the max. period
     * @return The required account age in ms as day units when we do not apply a payout delay anymore
     */
    public long getRequiredAccountAge(PaymentMethod paymentMethod) {
        switch (paymentMethod.getId()) {
            case PaymentMethod.BLOCK_CHAINS_ID:
            case PaymentMethod.BLOCK_CHAINS_INSTANT_ID:

            case PaymentMethod.US_POSTAL_MONEY_ORDER_ID:
            case PaymentMethod.HAL_CASH_ID:
            case PaymentMethod.F2F_ID:
            case PaymentMethod.MONEY_GRAM_ID:
            case PaymentMethod.WESTERN_UNION_ID:

            case PaymentMethod.SWISH_ID:
            case PaymentMethod.PERFECT_MONEY_ID:
            case PaymentMethod.ALI_PAY_ID:
            case PaymentMethod.WECHAT_PAY_ID:
                return 0;

            case PaymentMethod.ADVANCED_CASH_ID:
            case PaymentMethod.PROMPT_PAY_ID:
            case PaymentMethod.CASH_DEPOSIT_ID:
                return MAX_REQUIRED_AGE / 2 * DateUtils.MILLIS_PER_DAY;

            default:
                // All other bank transfer methods
                return MAX_REQUIRED_AGE * DateUtils.MILLIS_PER_DAY;
        }
    }

    /**
     * We take the buyer in a trade (maker or taker) into consideration for a delayed payout.
     * @param trade The trade for which we request the delayed payout state.
     * @return If that trade requires a delayed payout
     */
    public boolean tradeRequirePayoutDelay(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null) {
            return false;
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return false;
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return false;
        }

        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        long requiredAccountAge = getRequiredAccountAge(offer.getPaymentMethod());
        return buyersAccountAge < requiredAccountAge;
    }

    /**
     * We take the maker as buyer into consideration for a delayed payout.
     * @param offer The offer for which we request the delayed payout state.
     * @return If that offer requires a delayed payout
     */
    public boolean offerRequirePayoutDelay(Offer offer) {
        return accountRequiresPayoutDelay(offer.getPaymentMethod(),
                accountAgeWitnessService.getMakersAccountAge(offer),
                offer.getCurrencyCode(),
                offer.getDirection());
    }

    /**
     * My account is taken into consideration for a delayed payout.
     * @param myPaymentAccount      My payment account used for my offer
     * @param currencyCode          Currency code of my offer
     * @param direction             Direction of my offer
     * @return If my account requires a delayed payout
     */
    public boolean myMakerAccountRequiresPayoutDelay(PaymentAccount myPaymentAccount,
                                                     String currencyCode,
                                                     OfferPayload.Direction direction) {
        return accountRequiresPayoutDelay(myPaymentAccount.getPaymentMethod(),
                accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload()),
                currencyCode,
                direction);
    }

    /**
     * @param myPaymentAccount My payment account used for my offer
     * @return The AccountScoreCategory representing the account age.
     */
    public Optional<AccountScoreCategory> getMyAccountScoreCategory(PaymentAccount myPaymentAccount) {
        return Optional.of(getAccountScoreCategory(accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload())));
    }

    /**
     * @param offer The offer for which we request the AccountScoreCategory.
     * @return The AccountScoreCategory representing the account age.
     */
    public Optional<AccountScoreCategory> getAccountScoreCategoryOfMaker(Offer offer) {
        //TODO probably we want to show the AccountScoreCategory also for sellers
       /* if (offer.getDirection() == OfferPayload.Direction.SELL) {
            return Optional.empty();
        }*/

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return Optional.empty();
        }

        long makersAccountAge = accountAgeWitnessService.getMakersAccountAge(offer);
        return Optional.of(getAccountScoreCategory(makersAccountAge));
    }

    /**
     * @param trade The trade for which we request the AccountScoreCategory.
     * @return The AccountScoreCategory representing the account age.
     */
    public Optional<AccountScoreCategory> getAccountScoreCategoryOfBuyer(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null) {
            return Optional.empty();
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return Optional.empty();
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return Optional.empty();
        }

        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        return Optional.of(getAccountScoreCategory(buyersAccountAge));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean accountRequiresPayoutDelay(PaymentMethod paymentMethod,
                                               long accountAge,
                                               String currencyCode,
                                               OfferPayload.Direction direction) {
        if (direction == OfferPayload.Direction.SELL) {
            return false;
        }

        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return false;
        }

        long requiredAccountAge = getRequiredAccountAge(paymentMethod);
        return accountAge < requiredAccountAge;
    }

    private AccountScoreCategory getAccountScoreCategory(long accountAge) {
        long maxRequiredAge = MAX_REQUIRED_AGE * DateUtils.MILLIS_PER_DAY;
        if (accountAge >= maxRequiredAge) {
            return AccountScoreCategory.GOLD;
        } else if (accountAge >= maxRequiredAge / 2) {
            return AccountScoreCategory.SILVER;
        } else {
            return AccountScoreCategory.BRONZE;
        }
    }
}
