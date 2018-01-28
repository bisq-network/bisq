package io.bisq.core.payment;

import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.payload.PaymentMethod;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.core.payment.PaymentAccountUtil.getInfoForMismatchingPaymentMethodLimits;

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

        // check if we have a matching payment method or if its a bank account payment method which is treated special
        final boolean arePaymentMethodsEqual = paymentAccount.getPaymentMethod().equals(offer.getPaymentMethod());

        if (!arePaymentMethodsEqual &&
                paymentAccount.getPaymentMethod().getId().equals(offer.getPaymentMethod().getId())) {
            log.warn(getInfoForMismatchingPaymentMethodLimits(offer, paymentAccount));
        }

        if (!(paymentAccount instanceof CountryBasedPaymentAccount)) {
            return arePaymentMethodsEqual;
        }

        CountryBasedPaymentAccount countryBasedPaymentAccount = (CountryBasedPaymentAccount) paymentAccount;

        boolean matchesCountryCodes = isMatchesCountryCodes();
        if (!matchesCountryCodes) {
            return false;
        }

        // We have same country
        if (isSepaRelated()) {
            return arePaymentMethodsEqual;
        } else if (isSameOrSpecificBank()) {
            final List<String> acceptedBankIds = offer.getAcceptedBankIds();
            checkNotNull(acceptedBankIds, "offer.getAcceptedBankIds() must not be null");
            final String bankId = ((BankAccount) countryBasedPaymentAccount).getBankId();
            if (countryBasedPaymentAccount instanceof SpecificBanksAccount) {
                // check if we have a matching bank
                boolean offerSideMatchesBank = bankId != null && acceptedBankIds.contains(bankId);
                boolean paymentAccountSideMatchesBank = ((SpecificBanksAccount) countryBasedPaymentAccount).getAcceptedBanks().contains(offer.getBankId());
                return offerSideMatchesBank && paymentAccountSideMatchesBank;
            } else {
                // national or same bank
                return bankId != null && acceptedBankIds.contains(bankId);
            }
        } else {
            if (countryBasedPaymentAccount instanceof SpecificBanksAccount) {
                // check if we have a matching bank
                final ArrayList<String> acceptedBanks = ((SpecificBanksAccount) countryBasedPaymentAccount).getAcceptedBanks();
                return acceptedBanks != null && offer.getBankId() != null && acceptedBanks.contains(offer.getBankId());
            } else if (countryBasedPaymentAccount instanceof SameBankAccount) {
                // check if we have a matching bank
                final String bankId = ((SameBankAccount) countryBasedPaymentAccount).getBankId();
                return bankId != null && offer.getBankId() != null && bankId.equals(offer.getBankId());
            } else if (countryBasedPaymentAccount instanceof NationalBankAccount) {
                return true;
            } else if (countryBasedPaymentAccount instanceof WesternUnionAccount) {
                return offer.getPaymentMethod().equals(PaymentMethod.WESTERN_UNION);
            } else {
                log.warn("Not handled case at isPaymentAccountValidForOffer. paymentAccount={}. offer={}",
                        countryBasedPaymentAccount, offer);
                return false;
            }
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
