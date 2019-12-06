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

package bisq.core.payment;

import bisq.core.offer.Offer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class ReceiptValidator {
    private final ReceiptPredicates predicates;
    private final PaymentAccount account;
    private final Offer offer;

    ReceiptValidator(Offer offer, PaymentAccount account) {
        this(offer, account, new ReceiptPredicates());
    }

    ReceiptValidator(Offer offer, PaymentAccount account, ReceiptPredicates predicates) {
        this.offer = offer;
        this.account = account;
        this.predicates = predicates;
    }

    boolean isValid() {
        // We only support trades with the same currencies
        if (!predicates.isMatchingCurrency(offer, account)) {
            return false;
        }

        boolean isEqualPaymentMethods = predicates.isEqualPaymentMethods(offer, account);

        // All non-CountryBasedPaymentAccount need to have same payment methods
        if (!(account instanceof CountryBasedPaymentAccount)) {
            return isEqualPaymentMethods;
        }

        // We have a CountryBasedPaymentAccount, countries need to match
        if (!predicates.isMatchingCountryCodes(offer, account)) {
            return false;
        }

        // We have same country
        if (predicates.isMatchingSepaOffer(offer, account)) {
            // Sepa offer and taker account is Sepa or Sepa Instant
            return true;
        }

        if (predicates.isMatchingSepaInstant(offer, account)) {
            // Sepa Instant offer and taker account
            return true;
        }

        // Aside from Sepa or Sepa Instant, payment methods need to match
        if (!isEqualPaymentMethods) {
            return false;
        }

        if (predicates.isOfferRequireSameOrSpecificBank(offer, account)) {
            return predicates.isMatchingBankId(offer, account);
        }

        return true;
    }
}
