package io.bisq.api.model.payment;

import io.bisq.core.payment.UpholdAccount;

public class UpholdPaymentAccountConverter extends AbstractPaymentAccountConverter<UpholdAccount, UpholdPaymentAccount> {

    @Override
    public UpholdAccount toBusinessModel(UpholdPaymentAccount rest) {
        final UpholdAccount business = new UpholdAccount();
        business.init();
        business.setAccountId(rest.accountId);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public UpholdPaymentAccount toRestModel(UpholdAccount business) {
        final UpholdPaymentAccount rest = new UpholdPaymentAccount();
        rest.accountId = business.getAccountId();
        toRestModel(rest, business);
        return rest;
    }

}
