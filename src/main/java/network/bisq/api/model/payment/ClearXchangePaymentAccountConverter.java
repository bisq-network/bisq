package network.bisq.api.model.payment;

import bisq.core.payment.ClearXchangeAccount;
import bisq.core.payment.payload.ClearXchangeAccountPayload;

public class ClearXchangePaymentAccountConverter extends AbstractPaymentAccountConverter<ClearXchangeAccount, ClearXchangeAccountPayload, ClearXchangePaymentAccount> {

    @Override
    public ClearXchangeAccount toBusinessModel(ClearXchangePaymentAccount rest) {
        final ClearXchangeAccount business = new ClearXchangeAccount();
        business.init();
        business.setEmailOrMobileNr(rest.emailOrMobileNr);
        business.setHolderName(rest.holderName);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public ClearXchangePaymentAccount toRestModel(ClearXchangeAccount business) {
        final ClearXchangePaymentAccount rest = toRestModel((ClearXchangeAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public ClearXchangePaymentAccount toRestModel(ClearXchangeAccountPayload business) {
        final ClearXchangePaymentAccount rest = new ClearXchangePaymentAccount();
        rest.emailOrMobileNr = business.getEmailOrMobileNr();
        rest.holderName = business.getHolderName();
        toRestModel(rest, business);
        return rest;
    }

}
