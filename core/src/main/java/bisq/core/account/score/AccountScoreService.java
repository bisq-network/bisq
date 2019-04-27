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

package bisq.core.account.score;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;

import javax.inject.Inject;

import org.apache.commons.lang3.time.DateUtils;

public class AccountScoreService {
    public static final long BUYERS_MIN_ACCOUNT_AGE = 31 * DateUtils.MILLIS_PER_DAY;

    private final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public AccountScoreService(AccountAgeWitnessService accountAgeWitnessService) {
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    public boolean isFiatBuyerWithImmatureAccount(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null)
            return false;

        Contract contract = trade.getContract();
        if (contract == null)
            return false;

        boolean isFiatCurrency = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode());
        if (!isFiatCurrency)
            return false;

        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        long requiredAccountAge = getMinAccountAgeFactor(offer.getPaymentMethod());
        return buyersAccountAge < requiredAccountAge;
    }

    public long getMinAccountAgeFactor(PaymentMethod paymentMethod) {
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
                return BUYERS_MIN_ACCOUNT_AGE / 4;

            default:
                // All other bank transfer methods
                return BUYERS_MIN_ACCOUNT_AGE;
        }
    }
}
