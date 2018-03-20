package io.bisq.api.model.payment;

import io.bisq.core.payment.FasterPaymentsAccount;

public class FasterPaymentsPaymentAccountConverter extends AbstractPaymentAccountConverter<FasterPaymentsAccount, FasterPaymentsPaymentAccount> {

    @Override
    public FasterPaymentsAccount toBusinessModel(FasterPaymentsPaymentAccount rest) {
        final FasterPaymentsAccount business = new FasterPaymentsAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        business.setSortCode(rest.sortCode);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public FasterPaymentsPaymentAccount toRestModel(FasterPaymentsAccount business) {
        final FasterPaymentsPaymentAccount rest = new FasterPaymentsPaymentAccount();
        rest.accountNr = business.getAccountNr();
        rest.sortCode = business.getSortCode();
        toRestModel(rest, business);
        return rest;
    }

}
