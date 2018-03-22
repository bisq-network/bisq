package io.bisq.api.model.payment;

import io.bisq.core.payment.PerfectMoneyAccount;

public class PerfectMoneyPaymentAccountConverter extends AbstractPaymentAccountConverter<PerfectMoneyAccount, PerfectMoneyPaymentAccount> {

    @Override
    public PerfectMoneyAccount toBusinessModel(PerfectMoneyPaymentAccount rest) {
        final PerfectMoneyAccount business = new PerfectMoneyAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public PerfectMoneyPaymentAccount toRestModel(PerfectMoneyAccount business) {
        final PerfectMoneyPaymentAccount rest = new PerfectMoneyPaymentAccount();
        rest.accountNr = business.getAccountNr();
        toRestModel(rest, business);
        return rest;
    }

}
