package io.bisq.api.model.payment;

import io.bisq.common.locale.Country;
import io.bisq.common.locale.CountryUtil;
import io.bisq.core.payment.WesternUnionAccount;

public class WesternUnionPaymentAccountConverter extends AbstractPaymentAccountConverter<WesternUnionAccount, WesternUnionPaymentAccount> {

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
        final WesternUnionPaymentAccount rest = new WesternUnionPaymentAccount();
        rest.holderName = business.getFullName();
        rest.city = business.getCity();
        final Country country = business.getCountry();
        if (null != country)
            rest.countryCode = country.code;
        rest.email = business.getEmail();
        rest.state = business.getState();
        toRestModel(rest, business);
        return rest;
    }

}
