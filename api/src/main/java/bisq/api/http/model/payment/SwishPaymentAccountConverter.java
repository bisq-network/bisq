package bisq.api.http.model.payment;

import bisq.core.payment.SwishAccount;
import bisq.core.payment.payload.SwishAccountPayload;

public class SwishPaymentAccountConverter extends AbstractPaymentAccountConverter<SwishAccount, SwishAccountPayload, SwishPaymentAccount> {

    @Override
    public SwishAccount toBusinessModel(SwishPaymentAccount rest) {
        SwishAccount business = new SwishAccount();
        business.init();
        business.setMobileNr(rest.mobileNr);
        business.setHolderName(rest.holderName);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public SwishPaymentAccount toRestModel(SwishAccount business) {
        SwishPaymentAccount rest = toRestModel((SwishAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public SwishPaymentAccount toRestModel(SwishAccountPayload business) {
        SwishPaymentAccount rest = new SwishPaymentAccount();
        rest.mobileNr = business.getMobileNr();
        rest.holderName = business.getHolderName();
        toRestModel(rest, business);
        return rest;

    }

}
