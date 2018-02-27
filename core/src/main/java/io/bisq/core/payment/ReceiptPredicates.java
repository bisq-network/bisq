package io.bisq.core.payment;

import com.google.common.base.Preconditions;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.payload.PaymentMethod;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    boolean isSepaRelated(Offer offer, PaymentAccount account) {
        PaymentMethod offerPaymentMethod = offer.getPaymentMethod();
        return (account instanceof SepaAccount
                || account instanceof SepaInstantAccount)
                && (offerPaymentMethod.equals(PaymentMethod.SEPA)
                || offerPaymentMethod.equals(PaymentMethod.SEPA_INSTANT));
    }
}
