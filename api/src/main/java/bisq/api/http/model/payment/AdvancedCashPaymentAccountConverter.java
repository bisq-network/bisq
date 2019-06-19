package bisq.api.http.model.payment;

import bisq.core.payment.AdvancedCashAccount;
import bisq.core.payment.payload.AdvancedCashAccountPayload;

public class AdvancedCashPaymentAccountConverter extends AbstractPaymentAccountConverter<AdvancedCashAccount, AdvancedCashAccountPayload, AdvancedCashPaymentAccount> {

    @Override
    public AdvancedCashAccount toBusinessModel(AdvancedCashPaymentAccount rest) {
        AdvancedCashAccount business = new AdvancedCashAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public AdvancedCashPaymentAccount toRestModel(AdvancedCashAccount business) {
        AdvancedCashPaymentAccount rest = toRestModel((AdvancedCashAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public AdvancedCashPaymentAccount toRestModel(AdvancedCashAccountPayload business) {
        AdvancedCashPaymentAccount rest = new AdvancedCashPaymentAccount();
        rest.accountNr = business.getAccountNr();
        toRestModel(rest, business);
        return rest;
    }

}
