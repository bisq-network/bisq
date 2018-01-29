package io.bisq.core.payment;

import io.bisq.core.offer.Offer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static boolean isPaymentAccountValidForOffer(Offer offer, PaymentAccount paymentAccount) {
        return new ReceiptValidator(offer, paymentAccount).isValid();
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
