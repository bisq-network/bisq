package io.bisq.core.payment;

import io.bisq.core.offer.Offer;
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
        if (predicates.isSepaRelated(offer, account)) {
            // Sepa or Sepa Instant
            return true;
        } else if (predicates.isOfferRequireSameOrSpecificBank(offer, account)) {
            return predicates.isMatchingBankId(offer, account);
        } else {
            return isEqualPaymentMethods;
        }
    }
}
