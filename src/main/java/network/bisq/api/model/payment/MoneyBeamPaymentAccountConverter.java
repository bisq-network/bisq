package network.bisq.api.model.payment;

import bisq.core.payment.MoneyBeamAccount;
import bisq.core.payment.payload.MoneyBeamAccountPayload;

public class MoneyBeamPaymentAccountConverter extends AbstractPaymentAccountConverter<MoneyBeamAccount, MoneyBeamAccountPayload, MoneyBeamPaymentAccount> {

    @Override
    public MoneyBeamAccount toBusinessModel(MoneyBeamPaymentAccount rest) {
        final MoneyBeamAccount business = new MoneyBeamAccount();
        business.init();
        business.setAccountId(rest.accountId);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public MoneyBeamPaymentAccount toRestModel(MoneyBeamAccount business) {
        final MoneyBeamPaymentAccount rest = toRestModel((MoneyBeamAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public MoneyBeamPaymentAccount toRestModel(MoneyBeamAccountPayload business) {
        final MoneyBeamPaymentAccount rest = new MoneyBeamPaymentAccount();
        rest.accountId = business.getAccountId();
        toRestModel(rest, business);
        return rest;
    }

}
