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

import bisq.core.account.age.AccountCreationAgeService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Trade;

import javax.inject.Inject;

import java.util.Date;
import java.util.Optional;

/**
 * Main class for account score domain.
 * Provides access to any data related to account score. Internally it used different protection tools to constructing
 * the resulting parameters.
 */
public class AccountScoreService {
    private final AccountCreationAgeService accountCreationAgeService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountScoreService(AccountCreationAgeService accountCreationAgeService) {
        this.accountCreationAgeService = accountCreationAgeService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getRequiredAccountAge(PaymentMethod paymentMethod) {
        return accountCreationAgeService.getRequiredAccountAge(paymentMethod);
    }

    public boolean myMakerAccountRequiresPayoutDelay(PaymentAccount myPaymentAccount, String currencyCode, OfferPayload.Direction direction) {
        return accountCreationAgeService.myMakerAccountRequiresPayoutDelay(myPaymentAccount, currencyCode, direction);
    }

    public boolean offerRequirePayoutDelay(Offer offer) {
        return accountCreationAgeService.offerRequirePayoutDelay(offer);
    }

    public boolean tradeRequirePayoutDelay(Trade trade) {
        return accountCreationAgeService.tradeRequirePayoutDelay(trade);
    }

    public long getDelayForMyOffer(PaymentAccount myPaymentAccount, String currencyCode, OfferPayload.Direction direction) {
        return accountCreationAgeService.getDelayForMyOffer(myPaymentAccount, currencyCode, direction);
    }

    public long getDelayForOffer(Offer offer) {
        return accountCreationAgeService.getDelayForOffer(offer);
    }

    public Date getDelayedTradePayoutDate(Trade trade) {
        return accountCreationAgeService.getDelayedTradePayoutDate(trade);
    }

    public Optional<AccountScoreCategory> getMyAccountScoreCategory(PaymentAccount myPaymentAccount) {
        return accountCreationAgeService.getMyAccountScoreCategory(myPaymentAccount);
    }

    public Optional<AccountScoreCategory> getAccountScoreCategoryOfMaker(Offer offer) {
        return accountCreationAgeService.getAccountScoreCategoryOfMaker(offer);
    }

    public Optional<AccountScoreCategory> getAccountScoreCategoryOfBuyer(Trade trade) {
        return accountCreationAgeService.getAccountScoreCategoryOfBuyer(trade);
    }


}
