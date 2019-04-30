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

import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentMethod;

import com.google.common.base.Preconditions;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class ReceiptPredicates {
    boolean isEqualPaymentMethods(Offer offer, PaymentAccount account) {
        // check if we have a matching payment method or if its a bank account payment method which is treated special
        PaymentMethod accountPaymentMethod = account.getPaymentMethod();
        PaymentMethod offerPaymentMethod = offer.getPaymentMethod();

        boolean arePaymentMethodsEqual = accountPaymentMethod.equals(offerPaymentMethod);

        if (log.isWarnEnabled()) {
            String accountPaymentMethodId = accountPaymentMethod.getId();
            String offerPaymentMethodId = offerPaymentMethod.getId();
            if (!arePaymentMethodsEqual && accountPaymentMethodId.equals(offerPaymentMethodId)) {
                log.warn(PaymentAccountUtil.getInfoForMismatchingPaymentMethodLimits(offer, account));
            }
        }

        return arePaymentMethodsEqual;
    }

    boolean isOfferRequireSameOrSpecificBank(Offer offer, PaymentAccount account) {
        PaymentMethod paymentMethod = offer.getPaymentMethod();
        boolean isSameOrSpecificBank = paymentMethod.equals(PaymentMethod.SAME_BANK)
                || paymentMethod.equals(PaymentMethod.SPECIFIC_BANKS);
        return (account instanceof BankAccount) && isSameOrSpecificBank;
    }

    boolean isMatchingBankId(Offer offer, PaymentAccount account) {
        final List<String> acceptedBanksForOffer = offer.getAcceptedBankIds();
        Preconditions.checkNotNull(acceptedBanksForOffer, "offer.getAcceptedBankIds() must not be null");

        final String accountBankId = ((BankAccount) account).getBankId();

        if (account instanceof SpecificBanksAccount) {
            // check if we have a matching bank
            boolean offerSideMatchesBank = (accountBankId != null) && acceptedBanksForOffer.contains(accountBankId);
            List<String> acceptedBanksForAccount = ((SpecificBanksAccount) account).getAcceptedBanks();
            boolean paymentAccountSideMatchesBank = acceptedBanksForAccount.contains(offer.getBankId());

            return offerSideMatchesBank && paymentAccountSideMatchesBank;
        } else {
            // national or same bank
            return (accountBankId != null) && acceptedBanksForOffer.contains(accountBankId);
        }
    }

    boolean isMatchingCountryCodes(Offer offer, PaymentAccount account) {
        List<String> acceptedCodes = Optional.ofNullable(offer.getAcceptedCountryCodes())
                .orElse(Collections.emptyList());

        String code = Optional.of(account)
                .map(CountryBasedPaymentAccount.class::cast)
                .map(CountryBasedPaymentAccount::getCountry)
                .map(country -> country.code)
                .orElse("undefined");

        return acceptedCodes.contains(code);
    }

    boolean isMatchingCurrency(Offer offer, PaymentAccount account) {
        List<TradeCurrency> currencies = account.getTradeCurrencies();

        Set<String> codes = currencies.stream()
                .map(TradeCurrency::getCode)
                .collect(Collectors.toSet());

        return codes.contains(offer.getCurrencyCode());
    }

    boolean isMatchingSepaOffer(Offer offer, PaymentAccount account) {
        boolean isSepa = account instanceof SepaAccount;
        boolean isSepaInstant = account instanceof SepaInstantAccount;
        return offer.getPaymentMethod().equals(PaymentMethod.SEPA) && (isSepa || isSepaInstant);
    }

    boolean isMatchingSepaInstant(Offer offer, PaymentAccount account) {
        return offer.getPaymentMethod().equals(PaymentMethod.SEPA_INSTANT) && account instanceof SepaInstantAccount;
    }
}
