package io.bisq.api.model.payment;

import io.bisq.core.payment.RevolutAccount;

public class RevolutPaymentAccountConverter extends AbstractPaymentAccountConverter<RevolutAccount, RevolutPaymentAccount> {

    @Override
    public RevolutAccount toBusinessModel(RevolutPaymentAccount rest) {
        final RevolutAccount business = new RevolutAccount();
        business.init();
        business.setAccountId(rest.accountId);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public RevolutPaymentAccount toRestModel(RevolutAccount business) {
        final RevolutPaymentAccount rest = new RevolutPaymentAccount();
        rest.accountId = business.getAccountId();
        toRestModel(rest, business);
        return rest;
    }

}
