package io.bisq.core.payment;

import com.google.common.base.Preconditions;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.payload.PaymentMethod;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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
        if (!predicates.isMatchingCurrency(offer, account)) {
            return false;
        }

        boolean isEqualPaymentMethods = predicates.isEqualPaymentMethods(offer, account);

        if (!(account instanceof CountryBasedPaymentAccount)) {
            return isEqualPaymentMethods;
        }

        if (!predicates.isMatchingCountryCodes(offer, account)) {
            return false;
        }

        // We have same country
        if (predicates.isSepaRelated(offer, account)) {
            return isEqualPaymentMethods;
        } else if (predicates.isOfferRequireSameOrSpecificBank(offer, account)) {
            return isValidWhenOfferRequireSameOrSpecificBank();
        } else {
            return isValidByType();
        }
    }

    private boolean isValidWhenOfferRequireSameOrSpecificBank() {
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

    private boolean isValidByType() {
        if (account instanceof SpecificBanksAccount) {
            // check if we have a matching bank
            final List<String> acceptedBanksForAccount = ((SpecificBanksAccount) account).getAcceptedBanks();
            boolean paymentAccountSideMatchesBank = acceptedBanksForAccount.contains(offer.getBankId());

            return (offer.getBankId() != null) && paymentAccountSideMatchesBank;
        } else if (account instanceof SameBankAccount) {
            // check if we have a matching bank
            final String accountBankId = ((SameBankAccount) account).getBankId();
            return (accountBankId != null) && (offer.getBankId() != null) && accountBankId.equals(offer.getBankId());
        } else if (account instanceof NationalBankAccount) {
            return true;
        } else if (account instanceof WesternUnionAccount) {
            PaymentMethod paymentMethod = offer.getPaymentMethod();
            return paymentMethod.equals(PaymentMethod.WESTERN_UNION);
        } else {
            log.warn("Not handled case at isPaymentAccountValidForOffer. paymentAccount={}. offer={}",
                    account, offer);
            return false;
        }
    }
}
