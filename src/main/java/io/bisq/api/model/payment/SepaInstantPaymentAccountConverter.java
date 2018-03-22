package io.bisq.api.model.payment;

import io.bisq.common.locale.Country;
import io.bisq.common.locale.CountryUtil;
import io.bisq.core.payment.SepaInstantAccount;

import java.util.List;

public class SepaInstantPaymentAccountConverter extends AbstractPaymentAccountConverter<SepaInstantAccount, SepaInstantPaymentAccount> {

    @Override
    public SepaInstantAccount toBusinessModel(SepaInstantPaymentAccount rest) {
        final SepaInstantAccount business = new SepaInstantAccount();
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
    public SepaInstantPaymentAccount toRestModel(SepaInstantAccount business) {
        final SepaInstantPaymentAccount rest = new SepaInstantPaymentAccount();
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
