package io.bisq.api.model.payment;

import io.bisq.common.locale.Country;
import io.bisq.common.locale.CountryUtil;
import io.bisq.core.payment.SepaAccount;

import java.util.List;

public class SepaPaymentAccountConverter extends AbstractPaymentAccountConverter<SepaAccount, SepaPaymentAccount> {

    @Override
    public SepaAccount toBusinessModel(SepaPaymentAccount rest) {
        final SepaAccount business = new SepaAccount();
        business.init();
        business.setBic(rest.bic);
        business.setIban(rest.iban);
        business.setHolderName(rest.holderName);
        business.setCountry(CountryUtil.findCountryByCode(rest.countryCode).get());
        business.getAcceptedCountryCodes().clear();
        if (null != rest.acceptedCountries)
            rest.acceptedCountries.stream().forEach(business::addAcceptedCountry);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public SepaPaymentAccount toRestModel(SepaAccount business) {
        final SepaPaymentAccount rest = new SepaPaymentAccount();
        rest.iban = business.getIban();
        rest.bic = business.getBic();
        final Country country = business.getCountry();
        if (null != country)
            rest.countryCode = country.code;
        rest.holderName = business.getHolderName();
        final List<String> tradeCurrencies = business.getAcceptedCountryCodes();
        if (null != tradeCurrencies)
            rest.acceptedCountries.addAll(tradeCurrencies);
        toRestModel(rest, business);
        return rest;
    }

}
