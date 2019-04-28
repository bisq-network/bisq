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

import bisq.core.account.creation.AccountCreationAgeService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Trade;

import javax.inject.Inject;

import java.util.Date;

/**
 * Main class for account score domain.
 * Provides access to any data related to account score. Internally it used different protection tools to constructing
 * the resulting parameters.
 */
public class AccountScoreService {
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final AccountCreationAgeService accountCreationAgeService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountScoreService(AccountAgeWitnessService accountAgeWitnessService,
                               AccountCreationAgeService accountCreationAgeService) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.accountCreationAgeService = accountCreationAgeService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean requirePayoutDelay(Trade trade) {
        return accountCreationAgeService.requirePayoutDelay(trade);
    }

    public boolean requirePayoutDelay(Offer offer) {
        return accountCreationAgeService.requirePayoutDelay(offer);
    }


    public boolean myMakerAccountRequiresPayoutDelay(PaymentAccount myPaymentAccount, String currencyCode, OfferPayload.Direction direction) {
        return accountCreationAgeService.myMakerAccountRequiresPayoutDelay(myPaymentAccount, currencyCode, direction);
    }

    public Date getDelayAsDate(Trade trade) {
        return accountCreationAgeService.getDelayAsDate(trade);
    }

    public long getRequiredAccountAge(PaymentMethod paymentMethod) {
        return accountCreationAgeService.getRequiredAccountAge(paymentMethod);
    }
}
