package io.bisq.api.model.payment;

import io.bisq.core.payment.ChaseQuickPayAccount;
import io.bisq.core.payment.payload.ChaseQuickPayAccountPayload;

public class ChaseQuickPayPaymentAccountConverter extends AbstractPaymentAccountConverter<ChaseQuickPayAccount, ChaseQuickPayPaymentAccount> {

    @Override
    public ChaseQuickPayAccount toBusinessModel(ChaseQuickPayPaymentAccount rest) {
        final ChaseQuickPayAccount business = new ChaseQuickPayAccount();
        business.init();
        business.setEmail(rest.email);
        business.setHolderName(rest.holderName);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public ChaseQuickPayPaymentAccount toRestModel(ChaseQuickPayAccount business) {
        final ChaseQuickPayPaymentAccount rest = new ChaseQuickPayPaymentAccount();
        final ChaseQuickPayAccountPayload payload = (ChaseQuickPayAccountPayload) business.getPaymentAccountPayload();
        rest.email = payload.getEmail();
        rest.holderName = payload.getHolderName();
        toRestModel(rest, business);
        return rest;
    }

}
