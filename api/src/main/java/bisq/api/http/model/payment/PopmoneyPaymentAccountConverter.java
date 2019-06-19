package bisq.api.http.model.payment;

import bisq.core.payment.PopmoneyAccount;
import bisq.core.payment.payload.PopmoneyAccountPayload;

public class PopmoneyPaymentAccountConverter extends AbstractPaymentAccountConverter<PopmoneyAccount, PopmoneyAccountPayload, PopmoneyPaymentAccount> {

    @Override
    public PopmoneyAccount toBusinessModel(PopmoneyPaymentAccount rest) {
        PopmoneyAccount business = new PopmoneyAccount();
        business.init();
        business.setAccountId(rest.accountId);
        business.setHolderName(rest.holderName);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public PopmoneyPaymentAccount toRestModel(PopmoneyAccount business) {
        PopmoneyPaymentAccount rest = toRestModel((PopmoneyAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public PopmoneyPaymentAccount toRestModel(PopmoneyAccountPayload business) {
        PopmoneyPaymentAccount rest = new PopmoneyPaymentAccount();
        rest.accountId = business.getAccountId();
        rest.holderName = business.getHolderName();
        toRestModel(rest, business);
        return rest;

    }

}
