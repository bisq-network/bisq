package network.bisq.api.model.payment;

import bisq.core.locale.CountryUtil;
import bisq.core.payment.SepaAccount;
import bisq.core.payment.payload.SepaAccountPayload;

import java.util.List;

public class SepaPaymentAccountConverter extends AbstractPaymentAccountConverter<SepaAccount, SepaAccountPayload, SepaPaymentAccount> {

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
        final SepaPaymentAccount rest = toRestModel((SepaAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public SepaPaymentAccount toRestModel(SepaAccountPayload business) {
        final SepaPaymentAccount rest = new SepaPaymentAccount();
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
