package io.bisq.api.model.payment;

import io.bisq.core.payment.PaymentAccount;

public interface PaymentAccountConverter<B extends PaymentAccount, R extends io.bisq.api.model.PaymentAccount> {

    B toBusinessModel(R rest);

    R toRestModel(B business);

}
