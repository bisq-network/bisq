package bisq.api.http.model.payment;

import bisq.core.payment.UpholdAccount;
import bisq.core.payment.payload.UpholdAccountPayload;

public class UpholdPaymentAccountConverter extends AbstractPaymentAccountConverter<UpholdAccount, UpholdAccountPayload, UpholdPaymentAccount> {

    @Override
    public UpholdAccount toBusinessModel(UpholdPaymentAccount rest) {
        UpholdAccount business = new UpholdAccount();
        business.init();
        business.setAccountId(rest.accountId);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public UpholdPaymentAccount toRestModel(UpholdAccount business) {
        UpholdPaymentAccount rest = toRestModel((UpholdAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public UpholdPaymentAccount toRestModel(UpholdAccountPayload business) {
        UpholdPaymentAccount rest = new UpholdPaymentAccount();
        rest.accountId = business.getAccountId();
        toRestModel(rest, business);
        return rest;

    }

}
