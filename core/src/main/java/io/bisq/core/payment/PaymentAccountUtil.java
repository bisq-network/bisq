package io.bisq.core.payment;

import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.payload.PaymentMethod;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class PaymentAccountUtil {
    public static boolean isAnyPaymentAccountValidForOffer(Offer offer, Collection<PaymentAccount> paymentAccounts) {
        for (PaymentAccount paymentAccount : paymentAccounts) {
            if (isPaymentAccountValidForOffer(offer, paymentAccount))
                return true;
        }
        return false;
    }

    public static ObservableList<PaymentAccount> getPossiblePaymentAccounts(Offer offer, Set<PaymentAccount> paymentAccounts) {
        ObservableList<PaymentAccount> result = FXCollections.observableArrayList();
        result.addAll(paymentAccounts.stream()
                .filter(paymentAccount -> isPaymentAccountValidForOffer(offer, paymentAccount))
                .collect(Collectors.toList()));
        return result;
    }

    // TODO might be used to show more details if we get payment methods updates with diff. limits
    public static String getInfoForMismatchingPaymentMethodLimits(Offer offer, PaymentAccount paymentAccount) {
        // dont translate atm as it is not used so far in the UI just for logs
        return "Payment methods have different trade limits or trade periods.\n" +
                "Our local Payment method: " + paymentAccount.getPaymentMethod().toString() + "\n" +
                "Payment method from offer: " + offer.getPaymentMethod().toString();
    }

    //TODO not tested with all combinations yet....
    public static boolean isPaymentAccountValidForOffer(Offer offer, PaymentAccount paymentAccount) {
        // check if we have  a matching currency
        Set<String> paymentAccountCurrencyCodes = paymentAccount.getTradeCurrencies().stream()
                .map(TradeCurrency::getCode)
                .collect(Collectors.toSet());
        boolean matchesCurrencyCode = paymentAccountCurrencyCodes.contains(offer.getCurrencyCode());
        if (!matchesCurrencyCode)
            return false;

        // check if we have a matching payment method or if its a bank account payment method which is treated special
        final boolean arePaymentMethodsEqual = paymentAccount.getPaymentMethod().equals(offer.getPaymentMethod());

        if (!arePaymentMethodsEqual &&
                paymentAccount.getPaymentMethod().getId().equals(offer.getPaymentMethod().getId()))
            log.warn(getInfoForMismatchingPaymentMethodLimits(offer, paymentAccount));

        if (paymentAccount instanceof CountryBasedPaymentAccount) {
            CountryBasedPaymentAccount countryBasedPaymentAccount = (CountryBasedPaymentAccount) paymentAccount;

            // check if we have a matching country
            boolean matchesCountryCodes = offer.getAcceptedCountryCodes() != null && countryBasedPaymentAccount.getCountry() != null &&
                    offer.getAcceptedCountryCodes().contains(countryBasedPaymentAccount.getCountry().code);
            if (!matchesCountryCodes)
                return false;

            if (paymentAccount instanceof SepaAccount ||
                    paymentAccount instanceof SepaInstantAccount ||
                    offer.getPaymentMethod().equals(PaymentMethod.SEPA) ||
                    offer.getPaymentMethod().equals(PaymentMethod.SEPA_INSTANT)) {
                return arePaymentMethodsEqual;
            } else if (paymentAccount instanceof BankAccount && (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK) ||
                    offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS))) {

                final List<String> acceptedBankIds = offer.getAcceptedBankIds();
                checkNotNull(acceptedBankIds, "offer.getAcceptedBankIds() must not be null");
                final String bankId = ((BankAccount) paymentAccount).getBankId();
                if (paymentAccount instanceof SpecificBanksAccount) {
                    // check if we have a matching bank
                    boolean offerSideMatchesBank = bankId != null && acceptedBankIds.contains(bankId);
                    boolean paymentAccountSideMatchesBank = ((SpecificBanksAccount) paymentAccount).getAcceptedBanks().contains(offer.getBankId());
                    return offerSideMatchesBank && paymentAccountSideMatchesBank;
                } else {
                    // national or same bank
                    return bankId != null && acceptedBankIds.contains(bankId);
                }
            } else {
                //TODO check if that case can be reached
                if (paymentAccount instanceof SpecificBanksAccount) {
                    // check if we have a matching bank
                    final ArrayList<String> acceptedBanks = ((SpecificBanksAccount) paymentAccount).getAcceptedBanks();
                    return acceptedBanks != null && offer.getBankId() != null && acceptedBanks.contains(offer.getBankId());
                } else if (paymentAccount instanceof SameBankAccount) {
                    // check if we have a matching bank
                    final String bankId = ((SameBankAccount) paymentAccount).getBankId();
                    return bankId != null && offer.getBankId() != null && bankId.equals(offer.getBankId());
                } else {
                    // national
                    return true;
                }
            }
        } else {
            return arePaymentMethodsEqual;
        }
    }

    public static Optional<PaymentAccount> getMostMaturePaymentAccountForOffer(Offer offer,
                                                                               Set<PaymentAccount> paymentAccounts,
                                                                               AccountAgeWitnessService service) {
        List<PaymentAccount> list = paymentAccounts.stream()
                .filter(paymentAccount -> isPaymentAccountValidForOffer(offer, paymentAccount))
                .sorted((o1, o2) -> {
                    return new Long(service.getAccountAge(service.getMyWitness(o2.getPaymentAccountPayload()), new Date()))
                            .compareTo(service.getAccountAge(service.getMyWitness(o1.getPaymentAccountPayload()), new Date()));
                }).collect(Collectors.toList());
        list.stream().forEach(e -> log.debug("getMostMaturePaymentAccountForOffer AccountName={}, witnessHashAsHex={}", e.getAccountName(), service.getMyWitnessHashAsHex(e.getPaymentAccountPayload())));
        final Optional<PaymentAccount> first = list.stream().findFirst();
        if (first.isPresent())
            log.debug("first={}", first.get().getAccountName());
        return first;
    }
}
