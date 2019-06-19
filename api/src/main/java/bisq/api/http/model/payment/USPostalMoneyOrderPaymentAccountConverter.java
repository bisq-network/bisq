package bisq.api.http.model.payment;

import bisq.core.payment.USPostalMoneyOrderAccount;
import bisq.core.payment.payload.USPostalMoneyOrderAccountPayload;

public class USPostalMoneyOrderPaymentAccountConverter extends AbstractPaymentAccountConverter<USPostalMoneyOrderAccount, USPostalMoneyOrderAccountPayload, USPostalMoneyOrderPaymentAccount> {

    @Override
    public USPostalMoneyOrderAccount toBusinessModel(USPostalMoneyOrderPaymentAccount rest) {
        USPostalMoneyOrderAccount business = new USPostalMoneyOrderAccount();
        business.init();
        business.setHolderName(rest.holderName);
        business.setPostalAddress(rest.postalAddress);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public USPostalMoneyOrderPaymentAccount toRestModel(USPostalMoneyOrderAccount business) {
        USPostalMoneyOrderPaymentAccount rest = toRestModel((USPostalMoneyOrderAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public USPostalMoneyOrderPaymentAccount toRestModel(USPostalMoneyOrderAccountPayload business) {
        USPostalMoneyOrderPaymentAccount rest = new USPostalMoneyOrderPaymentAccount();
        rest.holderName = business.getHolderName();
        rest.postalAddress = business.getPostalAddress();
        toRestModel(rest, business);
        return rest;
    }

}
