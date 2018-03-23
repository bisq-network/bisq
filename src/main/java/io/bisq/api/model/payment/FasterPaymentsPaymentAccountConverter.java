package io.bisq.api.model.payment;

import io.bisq.core.payment.FasterPaymentsAccount;
import io.bisq.core.payment.payload.FasterPaymentsAccountPayload;

public class FasterPaymentsPaymentAccountConverter extends AbstractPaymentAccountConverter<FasterPaymentsAccount, FasterPaymentsAccountPayload, FasterPaymentsPaymentAccount> {

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
        final FasterPaymentsPaymentAccount rest = toRestModel((FasterPaymentsAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public FasterPaymentsPaymentAccount toRestModel(FasterPaymentsAccountPayload business) {
        final FasterPaymentsPaymentAccount rest = new FasterPaymentsPaymentAccount();
        rest.accountNr = business.getAccountNr();
        rest.sortCode = business.getSortCode();
        toRestModel(rest, business);
        return rest;
    }

}
