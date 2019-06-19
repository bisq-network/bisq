package bisq.api.http.model.payment;

import bisq.core.payment.PerfectMoneyAccount;
import bisq.core.payment.payload.PerfectMoneyAccountPayload;

public class PerfectMoneyPaymentAccountConverter extends AbstractPaymentAccountConverter<PerfectMoneyAccount, PerfectMoneyAccountPayload, PerfectMoneyPaymentAccount> {

    @Override
    public PerfectMoneyAccount toBusinessModel(PerfectMoneyPaymentAccount rest) {
        PerfectMoneyAccount business = new PerfectMoneyAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public PerfectMoneyPaymentAccount toRestModel(PerfectMoneyAccount business) {
        PerfectMoneyPaymentAccount rest = toRestModel((PerfectMoneyAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public PerfectMoneyPaymentAccount toRestModel(PerfectMoneyAccountPayload business) {
        PerfectMoneyPaymentAccount rest = new PerfectMoneyPaymentAccount();
        rest.accountNr = business.getAccountNr();
        toRestModel(rest, business);
        return rest;
    }

}
