package io.bisq.api.model.payment;

import io.bisq.core.payment.OKPayAccount;

public class OKPayPaymentAccountConverter extends AbstractPaymentAccountConverter<OKPayAccount, OKPayPaymentAccount> {

    @Override
    public OKPayAccount toBusinessModel(OKPayPaymentAccount rest) {
        final OKPayAccount business = new OKPayAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public OKPayPaymentAccount toRestModel(OKPayAccount business) {
        final OKPayPaymentAccount rest = new OKPayPaymentAccount();
        rest.accountNr = business.getAccountNr();
        toRestModel(rest, business);
        return rest;
    }

}
