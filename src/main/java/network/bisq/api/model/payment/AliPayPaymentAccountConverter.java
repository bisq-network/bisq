package network.bisq.api.model.payment;

import bisq.core.payment.AliPayAccount;
import bisq.core.payment.payload.AliPayAccountPayload;

public class AliPayPaymentAccountConverter extends AbstractPaymentAccountConverter<AliPayAccount, AliPayAccountPayload, AliPayPaymentAccount> {

    @Override
    public AliPayAccount toBusinessModel(AliPayPaymentAccount rest) {
        final AliPayAccount business = new AliPayAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public AliPayPaymentAccount toRestModel(AliPayAccount business) {
        final AliPayPaymentAccount rest = toRestModel((AliPayAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public AliPayPaymentAccount toRestModel(AliPayAccountPayload business) {
        final AliPayPaymentAccount rest = new AliPayPaymentAccount();
        rest.accountNr = business.getAccountNr();
        toRestModel(rest, business);
        return rest;
    }

}
