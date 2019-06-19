package bisq.api.http.model.payment;

import bisq.core.payment.RevolutAccount;
import bisq.core.payment.payload.RevolutAccountPayload;

public class RevolutPaymentAccountConverter extends AbstractPaymentAccountConverter<RevolutAccount, RevolutAccountPayload, RevolutPaymentAccount> {

    @Override
    public RevolutAccount toBusinessModel(RevolutPaymentAccount rest) {
        RevolutAccount business = new RevolutAccount();
        business.init();
        business.setAccountId(rest.accountId);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public RevolutPaymentAccount toRestModel(RevolutAccount business) {
        RevolutPaymentAccount rest = toRestModel((RevolutAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public RevolutPaymentAccount toRestModel(RevolutAccountPayload business) {
        RevolutPaymentAccount rest = new RevolutPaymentAccount();
        rest.accountId = business.getAccountId();
        toRestModel(rest, business);
        return rest;

    }

}
