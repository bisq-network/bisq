package bisq.httpapi.model.payment;

import bisq.core.locale.CountryUtil;
import bisq.core.payment.MoneyGramAccount;
import bisq.core.payment.payload.MoneyGramAccountPayload;

public class MoneyGramPaymentAccountConverter extends AbstractPaymentAccountConverter<MoneyGramAccount, MoneyGramAccountPayload, MoneyGramPaymentAccount> {

    @Override
    public MoneyGramAccount toBusinessModel(MoneyGramPaymentAccount rest) {
        final MoneyGramAccount business = new MoneyGramAccount();
        business.init();
        business.setCountry(CountryUtil.findCountryByCode(rest.countryCode).get());
        business.setEmail(rest.email);
        business.setFullName(rest.holderName);
        business.setState(rest.state);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public MoneyGramPaymentAccount toRestModel(MoneyGramAccount business) {
        final MoneyGramPaymentAccount rest = toRestModel((MoneyGramAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public MoneyGramPaymentAccount toRestModel(MoneyGramAccountPayload business) {
        final MoneyGramPaymentAccount rest = new MoneyGramPaymentAccount();
        rest.countryCode = business.getCountryCode();
        rest.email = business.getEmail();
        rest.holderName = business.getHolderName();
        rest.state = business.getState();
        toRestModel(rest, business);
        return rest;
    }

}
