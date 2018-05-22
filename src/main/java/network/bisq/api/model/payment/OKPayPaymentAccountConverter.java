package network.bisq.api.model.payment;

import bisq.core.payment.OKPayAccount;
import bisq.core.payment.payload.OKPayAccountPayload;

public class OKPayPaymentAccountConverter extends AbstractPaymentAccountConverter<OKPayAccount, OKPayAccountPayload, OKPayPaymentAccount> {

    @Override
    public OKPayAccount toBusinessModel(OKPayPaymentAccount rest) {
        final OKPayAccount business = new OKPayAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public OKPayPaymentAccount toRestModel(OKPayAccount business) {
        final OKPayPaymentAccount rest = toRestModel((OKPayAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public OKPayPaymentAccount toRestModel(OKPayAccountPayload business) {
        final OKPayPaymentAccount rest = new OKPayPaymentAccount();
        rest.accountNr = business.getAccountNr();
        toRestModel(rest, business);
        return rest;
    }

}
