package bisq.httpapi.model.payment;

import bisq.core.payment.F2FAccount;
import bisq.core.payment.payload.F2FAccountPayload;

public class F2FPaymentAccountConverter extends AbstractPaymentAccountConverter<F2FAccount, F2FAccountPayload, F2FPaymentAccount> {

    @Override
    public F2FAccount toBusinessModel(F2FPaymentAccount rest) {
        final F2FAccount business = new F2FAccount();
        business.init();
        business.setCity(rest.city);
        business.setContact(rest.contact);
        business.setExtraInfo(rest.extraInfo);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public F2FPaymentAccount toRestModel(F2FAccount business) {
        final F2FPaymentAccount rest = toRestModel((F2FAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public F2FPaymentAccount toRestModel(F2FAccountPayload business) {
        final F2FPaymentAccount rest = new F2FPaymentAccount();
        rest.city = business.getCity();
        rest.contact = business.getContact();
        rest.extraInfo = business.getExtraInfo();
        toRestModel(rest, business);
        return rest;
    }

}
