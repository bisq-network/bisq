package network.bisq.api.model.payment;

import bisq.core.payment.VenmoAccount;
import bisq.core.payment.payload.VenmoAccountPayload;

public class VenmoPaymentAccountConverter extends AbstractPaymentAccountConverter<VenmoAccount, VenmoAccountPayload, VenmoPaymentAccount> {

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
        final VenmoPaymentAccount rest = toRestModel((VenmoAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public VenmoPaymentAccount toRestModel(VenmoAccountPayload business) {
        final VenmoPaymentAccount rest = new VenmoPaymentAccount();
        rest.holderName = business.getHolderName();
        rest.venmoUserName = business.getVenmoUserName();
        toRestModel(rest, business);
        return rest;

    }

}
