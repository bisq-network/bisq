package network.bisq.api.model.payment;

import bisq.core.locale.CountryUtil;
import bisq.core.payment.WesternUnionAccount;
import bisq.core.payment.payload.WesternUnionAccountPayload;

public class WesternUnionPaymentAccountConverter extends AbstractPaymentAccountConverter<WesternUnionAccount, WesternUnionAccountPayload, WesternUnionPaymentAccount> {

    @Override
    public WesternUnionAccount toBusinessModel(WesternUnionPaymentAccount rest) {
        final WesternUnionAccount business = new WesternUnionAccount();
        business.init();
        business.setFullName(rest.holderName);
        business.setCity(rest.city);
        business.setCountry(CountryUtil.findCountryByCode(rest.countryCode).get());
        business.setEmail(rest.email);
        business.setState(rest.state);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public WesternUnionPaymentAccount toRestModel(WesternUnionAccount business) {
        final WesternUnionPaymentAccount rest = toRestModel((WesternUnionAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public WesternUnionPaymentAccount toRestModel(WesternUnionAccountPayload business) {
        final WesternUnionPaymentAccount rest = new WesternUnionPaymentAccount();
        rest.holderName = business.getHolderName();
        rest.city = business.getCity();
        rest.countryCode = business.getCountryCode();
        rest.email = business.getEmail();
        rest.state = business.getState();
        toRestModel(rest, business);
        return rest;

    }

}
