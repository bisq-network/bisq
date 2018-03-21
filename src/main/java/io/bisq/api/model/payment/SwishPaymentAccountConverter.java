package io.bisq.api.model.payment;

import io.bisq.core.payment.SwishAccount;
import io.bisq.core.payment.payload.SwishAccountPayload;

public class SwishPaymentAccountConverter extends AbstractPaymentAccountConverter<SwishAccount, SwishPaymentAccount> {

    @Override
    public SwishAccount toBusinessModel(SwishPaymentAccount rest) {
        final SwishAccount business = new SwishAccount();
        business.init();
        business.setMobileNr(rest.mobileNr);
        business.setHolderName(rest.holderName);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public SwishPaymentAccount toRestModel(SwishAccount business) {
        final SwishPaymentAccount rest = new SwishPaymentAccount();
        final SwishAccountPayload payload = (SwishAccountPayload) business.getPaymentAccountPayload();
        rest.mobileNr = payload.getMobileNr();
        rest.holderName = payload.getHolderName();
        toRestModel(rest, business);
        return rest;
    }

}
