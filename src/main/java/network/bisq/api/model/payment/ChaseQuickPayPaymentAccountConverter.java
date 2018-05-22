package network.bisq.api.model.payment;

import bisq.core.payment.ChaseQuickPayAccount;
import bisq.core.payment.payload.ChaseQuickPayAccountPayload;

public class ChaseQuickPayPaymentAccountConverter extends AbstractPaymentAccountConverter<ChaseQuickPayAccount, ChaseQuickPayAccountPayload, ChaseQuickPayPaymentAccount> {

    @Override
    public ChaseQuickPayAccount toBusinessModel(ChaseQuickPayPaymentAccount rest) {
        final ChaseQuickPayAccount business = new ChaseQuickPayAccount();
        business.init();
        business.setEmail(rest.email);
        business.setHolderName(rest.holderName);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public ChaseQuickPayPaymentAccount toRestModel(ChaseQuickPayAccount business) {
        final ChaseQuickPayPaymentAccount rest = toRestModel((ChaseQuickPayAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public ChaseQuickPayPaymentAccount toRestModel(ChaseQuickPayAccountPayload business) {
        final ChaseQuickPayPaymentAccount rest = new ChaseQuickPayPaymentAccount();
        rest.email = business.getEmail();
        rest.holderName = business.getHolderName();
        toRestModel(rest, business);
        return rest;
    }

}
