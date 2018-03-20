package io.bisq.api.model.payment;

import io.bisq.core.payment.AliPayAccount;

public class AliPayPaymentAccountConverter extends AbstractPaymentAccountConverter<AliPayAccount, AliPayPaymentAccount> {

    @Override
    public AliPayAccount toBusinessModel(AliPayPaymentAccount rest) {
        final AliPayAccount business = new AliPayAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public AliPayPaymentAccount toRestModel(AliPayAccount business) {
        final AliPayPaymentAccount rest = new AliPayPaymentAccount();
        rest.accountNr = business.getAccountNr();
        toRestModel(rest, business);
        return rest;
    }

}
