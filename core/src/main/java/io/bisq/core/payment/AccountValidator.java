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
class AccountValidator {
    private final Offer offer;
    private final PaymentAccount paymentAccount;

    AccountValidator(Offer offer, PaymentAccount paymentAccount) {
        this.offer = offer;
        this.paymentAccount = paymentAccount;
    }

    boolean isPaymentAccountValidForOffer() {
        if (!isMatchingCurrency()) {
            return false;
        }

        final boolean isEqualPaymentMethods = isEqualPaymentMethods();

        if (!(paymentAccount instanceof CountryBasedPaymentAccount)) {
            return isEqualPaymentMethods;
        }

        if (!isMatchesCountryCodes()) {
            return false;
        }

        // We have same country
        if (isSepaRelated()) {
            return isEqualPaymentMethods;
        } else if (isSameOrSpecificBank()) {
            return isValidForSameOrSpecificBankAccount();
        } else {
            return isValidByType();
        }
    }

    private boolean isEqualPaymentMethods() {
        // check if we have a matching payment method or if its a bank account payment method which is treated special
        PaymentMethod accountPaymentMethod = paymentAccount.getPaymentMethod();
        PaymentMethod offerPaymentMethod = offer.getPaymentMethod();

        final boolean arePaymentMethodsEqual = accountPaymentMethod.equals(offerPaymentMethod);

        if (log.isWarnEnabled()) {
            String accountPaymentMethodId = accountPaymentMethod.getId();
            String offerPaymentMethodId = offerPaymentMethod.getId();
            if (!arePaymentMethodsEqual && accountPaymentMethodId.equals(offerPaymentMethodId)) {
                log.warn(PaymentAccountUtil.getInfoForMismatchingPaymentMethodLimits(offer, paymentAccount));
            }
        }

        return arePaymentMethodsEqual;
    }

    private boolean isValidByType() {
        if (paymentAccount instanceof SpecificBanksAccount) {
            // check if we have a matching bank
            final List<String> acceptedBanksForAccount = ((SpecificBanksAccount) paymentAccount).getAcceptedBanks();
            boolean paymentAccountSideMatchesBank = acceptedBanksForAccount.contains(offer.getBankId());

            return (offer.getBankId() != null) && paymentAccountSideMatchesBank;
        } else if (paymentAccount instanceof SameBankAccount) {
            // check if we have a matching bank
            final String accountBankId = ((SameBankAccount) paymentAccount).getBankId();
            return (accountBankId != null) && (offer.getBankId() != null) && accountBankId.equals(offer.getBankId());
        } else if (paymentAccount instanceof NationalBankAccount) {
            return true;
        } else if (paymentAccount instanceof WesternUnionAccount) {
            PaymentMethod paymentMethod = offer.getPaymentMethod();
            return paymentMethod.equals(PaymentMethod.WESTERN_UNION);
        } else {
            log.warn("Not handled case at isPaymentAccountValidForOffer. paymentAccount={}. offer={}",
                    paymentAccount, offer);
            return false;
        }
    }

    private boolean isValidForSameOrSpecificBankAccount() {
        final List<String> acceptedBanksForOffer = offer.getAcceptedBankIds();
        Preconditions.checkNotNull(acceptedBanksForOffer, "offer.getAcceptedBankIds() must not be null");

        final String accountBankId = ((BankAccount) paymentAccount).getBankId();

        if (paymentAccount instanceof SpecificBanksAccount) {
            // check if we have a matching bank
            boolean offerSideMatchesBank = (accountBankId != null) && acceptedBanksForOffer.contains(accountBankId);
            List<String> acceptedBanksForAccount = ((SpecificBanksAccount) paymentAccount).getAcceptedBanks();
            boolean paymentAccountSideMatchesBank = acceptedBanksForAccount.contains(offer.getBankId());

            return offerSideMatchesBank && paymentAccountSideMatchesBank;
        } else {
            // national or same bank
            return (accountBankId != null) && acceptedBanksForOffer.contains(accountBankId);
        }
    }

    private boolean isSameOrSpecificBank() {
        PaymentMethod paymentMethod = offer.getPaymentMethod();
        boolean isSameOrSpecificBank = paymentMethod.equals(PaymentMethod.SAME_BANK)
                || paymentMethod.equals(PaymentMethod.SPECIFIC_BANKS);
        return (paymentAccount instanceof BankAccount) && isSameOrSpecificBank;
    }

    private boolean isMatchesCountryCodes() {
        List<String> acceptedCodes = Optional.ofNullable(offer.getAcceptedCountryCodes())
                .orElse(Collections.emptyList());

        String code = Optional.of(paymentAccount)
                .map(CountryBasedPaymentAccount.class::cast)
                .map(CountryBasedPaymentAccount::getCountry)
                .map(country -> country.code)
                .orElse("undefined");

        return acceptedCodes.contains(code);
    }

    private boolean isMatchingCurrency() {
        List<TradeCurrency> currencies = paymentAccount.getTradeCurrencies();

        Set<String> codes = currencies.stream()
                .map(TradeCurrency::getCode)
                .collect(Collectors.toSet());

        return codes.contains(offer.getCurrencyCode());
    }

    private boolean isSepaRelated() {
        PaymentMethod paymentMethod = offer.getPaymentMethod();
        return paymentAccount instanceof SepaAccount
                || paymentAccount instanceof SepaInstantAccount
                || paymentMethod.equals(PaymentMethod.SEPA)
                || paymentMethod.equals(PaymentMethod.SEPA_INSTANT);
    }
}
