package bisq.api.http.model.payment;

import bisq.core.locale.CountryUtil;
import bisq.core.payment.SepaAccount;
import bisq.core.payment.payload.SepaAccountPayload;

import java.util.List;

public class SepaPaymentAccountConverter extends AbstractPaymentAccountConverter<SepaAccount, SepaAccountPayload, SepaPaymentAccount> {

    @Override
    public SepaAccount toBusinessModel(SepaPaymentAccount rest) {
        SepaAccount business = new SepaAccount();
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
    public SepaPaymentAccount toRestModel(SepaAccount business) {
        SepaPaymentAccount rest = toRestModel((SepaAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public SepaPaymentAccount toRestModel(SepaAccountPayload business) {
        SepaPaymentAccount rest = new SepaPaymentAccount();
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
