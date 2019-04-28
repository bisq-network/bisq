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

package bisq.core.account.creation;

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

import lombok.extern.slf4j.Slf4j;

/**
 * Responsible for delayed payout based on account age. It does not consider if the account was yet used for
 * trading and is therefor not considered a strong protection.
 */
@Slf4j
public class AccountCreationAgeService {

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
     * @param buyersAccountAge      Account age of buyer
     * @param requiredAccountAge    Required account age
     * @return The delay for the delayed payout.
     */
    @VisibleForTesting
    public static long getDelay(long buyersAccountAge, long requiredAccountAge) {
        return Math.round(Math.max(0, (double) (requiredAccountAge - buyersAccountAge) / (double) requiredAccountAge * 28d));
    }

    /**
     * @param trade     The trade for which we want to know the delayed payout for the buyer.
     * @return The delay for the payout for the fiat buyer in a trade.
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

        return getDelay(buyersAccountAge, requiredAccountAge);
    }

    /**
     * @param trade     The trade wor which we want to know the delayed payout for the buyer.
     * @return The date of a delayed payout
     */
    public Date getDelayAsDate(Trade trade) {
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
                return 21 * DateUtils.MILLIS_PER_DAY;

            default:
                // All other bank transfer methods
                return 42 * DateUtils.MILLIS_PER_DAY;
        }
    }

    /**
     * We take the buyer in a trade (maker or taker) into consideration for a delayed payout.
     * @param trade The trade for which we request the delayed payout state.
     * @return If that trade requires a delayed payout
     */
    public boolean requirePayoutDelay(Trade trade) {
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
    public boolean requirePayoutDelay(Offer offer) {
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
}
