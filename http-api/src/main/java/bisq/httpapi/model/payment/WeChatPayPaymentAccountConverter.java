package bisq.httpapi.model.payment;

import bisq.core.payment.WeChatPayAccount;
import bisq.core.payment.payload.WeChatPayAccountPayload;

public class WeChatPayPaymentAccountConverter extends AbstractPaymentAccountConverter<WeChatPayAccount, WeChatPayAccountPayload, WeChatPayPaymentAccount> {

    @Override
    public WeChatPayAccount toBusinessModel(WeChatPayPaymentAccount rest) {
        final WeChatPayAccount business = new WeChatPayAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public WeChatPayPaymentAccount toRestModel(WeChatPayAccount business) {
        final WeChatPayPaymentAccount rest = toRestModel((WeChatPayAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public WeChatPayPaymentAccount toRestModel(WeChatPayAccountPayload business) {
        final WeChatPayPaymentAccount rest = new WeChatPayPaymentAccount();
        rest.accountNr = business.getAccountNr();
        toRestModel(rest, business);
        return rest;

    }

}
