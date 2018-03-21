package io.bisq.api.model.payment;

import io.bisq.core.payment.VenmoAccount;

public class VenmoPaymentAccountConverter extends AbstractPaymentAccountConverter<VenmoAccount, VenmoPaymentAccount> {

    @Override
    public VenmoAccount toBusinessModel(VenmoPaymentAccount rest) {
        final VenmoAccount business = new VenmoAccount();
        business.init();
        business.setHolderName(rest.holderName);
        business.setVenmoUserName(rest.venmoUserName);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public VenmoPaymentAccount toRestModel(VenmoAccount business) {
        final VenmoPaymentAccount rest = new VenmoPaymentAccount();
        rest.holderName = business.getHolderName();
        rest.venmoUserName = business.getVenmoUserName();
        toRestModel(rest, business);
        return rest;
    }

}
