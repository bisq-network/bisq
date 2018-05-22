package network.bisq.api.model.payment;

import bisq.core.payment.CashAppAccount;
import bisq.core.payment.payload.CashAppAccountPayload;

public class CashAppPaymentAccountConverter extends AbstractPaymentAccountConverter<CashAppAccount, CashAppAccountPayload, CashAppPaymentAccount> {

    @Override
    public CashAppAccount toBusinessModel(CashAppPaymentAccount rest) {
        final CashAppAccount business = new CashAppAccount();
        business.init();
        business.setCashTag(rest.cashTag);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public CashAppPaymentAccount toRestModel(CashAppAccount business) {
        final CashAppPaymentAccount rest = toRestModel((CashAppAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public CashAppPaymentAccount toRestModel(CashAppAccountPayload business) {
        final CashAppPaymentAccount rest = new CashAppPaymentAccount();
        rest.cashTag = business.getCashTag();
        toRestModel(rest, business);
        return rest;

    }

}
