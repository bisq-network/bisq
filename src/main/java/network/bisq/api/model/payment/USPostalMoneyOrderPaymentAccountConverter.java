package network.bisq.api.model.payment;

import bisq.core.payment.USPostalMoneyOrderAccount;
import bisq.core.payment.payload.USPostalMoneyOrderAccountPayload;

public class USPostalMoneyOrderPaymentAccountConverter extends AbstractPaymentAccountConverter<USPostalMoneyOrderAccount, USPostalMoneyOrderAccountPayload, USPostalMoneyOrderPaymentAccount> {

    @Override
    public USPostalMoneyOrderAccount toBusinessModel(USPostalMoneyOrderPaymentAccount rest) {
        final USPostalMoneyOrderAccount business = new USPostalMoneyOrderAccount();
        business.init();
        business.setHolderName(rest.holderName);
        business.setPostalAddress(rest.postalAddress);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public USPostalMoneyOrderPaymentAccount toRestModel(USPostalMoneyOrderAccount business) {
        final USPostalMoneyOrderPaymentAccount rest = toRestModel((USPostalMoneyOrderAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public USPostalMoneyOrderPaymentAccount toRestModel(USPostalMoneyOrderAccountPayload business) {
        final USPostalMoneyOrderPaymentAccount rest = new USPostalMoneyOrderPaymentAccount();
        rest.holderName = business.getHolderName();
        rest.postalAddress = business.getPostalAddress();
        toRestModel(rest, business);
        return rest;
    }

}
