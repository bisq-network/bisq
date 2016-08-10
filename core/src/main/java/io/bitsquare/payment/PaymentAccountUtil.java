package io.bitsquare.payment;

import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.offer.Offer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class PaymentAccountUtil {
    private static final Logger log = LoggerFactory.getLogger(PaymentAccountUtil.class);

    public static boolean isAnyPaymentAccountValidForOffer(Offer offer, Collection<PaymentAccount> paymentAccounts) {
        for (PaymentAccount paymentAccount : paymentAccounts) {
            if (isPaymentAccountValidForOffer(offer, paymentAccount))
                return true;
        }
        return false;
    }

    public static ObservableList<PaymentAccount> getPossiblePaymentAccounts(Offer offer, Set<PaymentAccount> paymentAccounts) {
        ObservableList<PaymentAccount> result = FXCollections.observableArrayList();
        for (PaymentAccount paymentAccount : paymentAccounts) {
            if (isPaymentAccountValidForOffer(offer, paymentAccount))
                result.add(paymentAccount);
        }
        return result;
    }

    //TODO not tested with all combinations yet....
    public static boolean isPaymentAccountValidForOffer(Offer offer, PaymentAccount paymentAccount) {
        // check if we have  a matching currency
        Set<String> paymentAccountCurrencyCodes = paymentAccount.getTradeCurrencies().stream().map(TradeCurrency::getCode).collect(Collectors.toSet());
        boolean matchesCurrencyCode = paymentAccountCurrencyCodes.contains(offer.getCurrencyCode());
        if (!matchesCurrencyCode)
            return false;

        // check if we have a matching payment method or if its a bank account payment method which is treated special
        if (paymentAccount instanceof CountryBasedPaymentAccount) {
            CountryBasedPaymentAccount countryBasedPaymentAccount = (CountryBasedPaymentAccount) paymentAccount;

            // check if we have a matching country
            boolean matchesCountryCodes = offer.getAcceptedCountryCodes() != null && countryBasedPaymentAccount.getCountry() != null &&
                    offer.getAcceptedCountryCodes().contains(countryBasedPaymentAccount.getCountry().code);
            if (!matchesCountryCodes)
                return false;

            if (paymentAccount instanceof SepaAccount || offer.getPaymentMethod().equals(PaymentMethod.SEPA)) {
                return paymentAccount.getPaymentMethod().equals(offer.getPaymentMethod());
            } else if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK) ||
                    offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS)) {

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
            return paymentAccount.getPaymentMethod().equals(offer.getPaymentMethod());
        }
    }

}
