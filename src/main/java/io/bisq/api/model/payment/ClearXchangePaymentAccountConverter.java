package io.bisq.api.model.payment;

import io.bisq.core.payment.ClearXchangeAccount;
import io.bisq.core.payment.payload.ClearXchangeAccountPayload;

public class ClearXchangePaymentAccountConverter extends AbstractPaymentAccountConverter<ClearXchangeAccount, ClearXchangePaymentAccount> {

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
        final ClearXchangePaymentAccount rest = new ClearXchangePaymentAccount();
        final ClearXchangeAccountPayload payload = (ClearXchangeAccountPayload) business.getPaymentAccountPayload();
        rest.emailOrMobileNr = payload.getEmailOrMobileNr();
        rest.holderName = payload.getHolderName();
        toRestModel(rest, business);
        return rest;
    }

}
