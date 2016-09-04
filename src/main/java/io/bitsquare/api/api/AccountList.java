package io.bitsquare.api.api;

import com.fasterxml.jackson.annotation.JsonValue;
import io.bitsquare.payment.PaymentAccount;

import java.util.List;
import java.util.Set;

/**
 * Created by mike on 04/09/16.
 */
public class AccountList {
    public Set<PaymentAccount> paymentAccounts;

    @JsonValue
    public Set<PaymentAccount> getPaymentAccounts() {
        return paymentAccounts;
    }
}
