package bisq.api.http.model.payment;

import bisq.core.locale.CountryUtil;
import bisq.core.payment.SepaInstantAccount;
import bisq.core.payment.payload.SepaInstantAccountPayload;

import java.util.List;

public class SepaInstantPaymentAccountConverter extends AbstractPaymentAccountConverter<SepaInstantAccount, SepaInstantAccountPayload, SepaInstantPaymentAccount> {

    @Override
    public SepaInstantAccount toBusinessModel(SepaInstantPaymentAccount rest) {
        SepaInstantAccount business = new SepaInstantAccount();
        business.init();
        business.setBic(rest.bic);
        business.setIban(rest.iban);
        business.setHolderName(rest.holderName);
        CountryUtil.findCountryByCode(rest.countryCode).ifPresent(business::setCountry);
        business.getAcceptedCountryCodes().clear();
        if (rest.acceptedCountries != null)
            rest.acceptedCountries.forEach(business::addAcceptedCountry);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public SepaInstantPaymentAccount toRestModel(SepaInstantAccount business) {
        SepaInstantPaymentAccount rest = toRestModel((SepaInstantAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public SepaInstantPaymentAccount toRestModel(SepaInstantAccountPayload business) {
        SepaInstantPaymentAccount rest = new SepaInstantPaymentAccount();
        rest.iban = business.getIban();
        rest.bic = business.getBic();
        rest.countryCode = business.getCountryCode();
        rest.holderName = business.getHolderName();
        List<String> tradeCurrencies = business.getAcceptedCountryCodes();
        if (tradeCurrencies != null)
            rest.acceptedCountries.addAll(tradeCurrencies);
        toRestModel(rest, business);
        return rest;

    }

}
