package io.bisq.api.model.payment;

import io.bisq.core.payment.CashAppAccount;

public class CashAppPaymentAccountConverter extends AbstractPaymentAccountConverter<CashAppAccount, CashAppPaymentAccount> {

    @Override
    public CashAppAccount toBusinessModel(CashAppPaymentAccount rest) {
        final CashAppAccount business = new CashAppAccount();
        business.init();
        business.setCashTag(rest.cashTag);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public CashAppPaymentAccount toRestModel(CashAppAccount business) {
        final CashAppPaymentAccount rest = new CashAppPaymentAccount();
        rest.cashTag = business.getCashTag();
        toRestModel(rest, business);
        return rest;
    }

}
