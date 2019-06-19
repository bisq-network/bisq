package bisq.api.http.model.payment;

import bisq.core.payment.PromptPayAccount;
import bisq.core.payment.payload.PromptPayAccountPayload;

public class PromptPayPaymentAccountConverter extends AbstractPaymentAccountConverter<PromptPayAccount, PromptPayAccountPayload, PromptPayPaymentAccount> {

    @Override
    public PromptPayAccount toBusinessModel(PromptPayPaymentAccount rest) {
        PromptPayAccount business = new PromptPayAccount();
        business.init();
        business.setPromptPayId(rest.promptPayId);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public PromptPayPaymentAccount toRestModel(PromptPayAccount business) {
        PromptPayPaymentAccount rest = toRestModel((PromptPayAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public PromptPayPaymentAccount toRestModel(PromptPayAccountPayload business) {
        PromptPayPaymentAccount rest = new PromptPayPaymentAccount();
        rest.promptPayId = business.getPromptPayId();
        toRestModel(rest, business);
        return rest;
    }

}
