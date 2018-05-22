package network.bisq.api.model.payment;

import bisq.core.locale.CountryUtil;
import bisq.core.payment.SepaInstantAccount;
import bisq.core.payment.payload.SepaInstantAccountPayload;

import java.util.List;

public class SepaInstantPaymentAccountConverter extends AbstractPaymentAccountConverter<SepaInstantAccount, SepaInstantAccountPayload, SepaInstantPaymentAccount> {

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
        final SepaInstantPaymentAccount rest = toRestModel((SepaInstantAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public SepaInstantPaymentAccount toRestModel(SepaInstantAccountPayload business) {
        final SepaInstantPaymentAccount rest = new SepaInstantPaymentAccount();
        rest.iban = business.getIban();
        rest.bic = business.getBic();
        rest.countryCode = business.getCountryCode();
        rest.holderName = business.getHolderName();
        final List<String> tradeCurrencies = business.getAcceptedCountryCodes();
        if (null != tradeCurrencies)
            rest.acceptedCountries.addAll(tradeCurrencies);
        toRestModel(rest, business);
        return rest;

    }

}
