package bisq.api.http.facade;

import bisq.api.http.exceptions.NotFoundException;
import bisq.api.http.model.PaymentAccountList;
import bisq.api.http.model.payment.PaymentAccountHelper;

import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountManager;
import bisq.core.user.User;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PaymentAccountFacade {

    private final PaymentAccountManager paymentAccountManager;
    private final User user;

    @Inject
    public PaymentAccountFacade(PaymentAccountManager paymentAccountManager, User user) {
        this.paymentAccountManager = paymentAccountManager;
        this.user = user;
    }

    public PaymentAccount addPaymentAccount(PaymentAccount paymentAccount) {
        return paymentAccountManager.addPaymentAccount(paymentAccount);
    }

    public void removePaymentAccount(String id) {
        PaymentAccount paymentAccount = user.getPaymentAccount(id);
        if (paymentAccount == null) {
            throw new NotFoundException("Payment account not found: " + id);
        }
        user.removePaymentAccount(paymentAccount);
    }

    public PaymentAccountList getAccountList() {
        PaymentAccountList paymentAccountList = new PaymentAccountList();
        paymentAccountList.paymentAccounts = getPaymentAccountList().stream()
                .map(PaymentAccountHelper::toRestModel)
                .collect(Collectors.toList());
        return paymentAccountList;
    }

    private List<PaymentAccount> getPaymentAccountList() {
        Set<PaymentAccount> paymentAccounts = user.getPaymentAccounts();
        return null == paymentAccounts ? Collections.emptyList() : new ArrayList<>(paymentAccounts);
    }
}
