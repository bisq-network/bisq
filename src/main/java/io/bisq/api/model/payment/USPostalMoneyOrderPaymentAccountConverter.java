package io.bisq.api.model.payment;

import io.bisq.core.payment.USPostalMoneyOrderAccount;

public class USPostalMoneyOrderPaymentAccountConverter extends AbstractPaymentAccountConverter<USPostalMoneyOrderAccount, USPostalMoneyOrderPaymentAccount> {

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
        final USPostalMoneyOrderPaymentAccount rest = new USPostalMoneyOrderPaymentAccount();
        rest.holderName = business.getHolderName();
        rest.postalAddress = business.getPostalAddress();
        toRestModel(rest, business);
        return rest;
    }

}
