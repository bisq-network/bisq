package bisq.api.http.model.payment;

import bisq.core.locale.CountryUtil;
import bisq.core.payment.MoneyGramAccount;
import bisq.core.payment.payload.MoneyGramAccountPayload;

public class MoneyGramPaymentAccountConverter extends AbstractPaymentAccountConverter<MoneyGramAccount, MoneyGramAccountPayload, MoneyGramPaymentAccount> {

    @Override
    public MoneyGramAccount toBusinessModel(MoneyGramPaymentAccount rest) {
        MoneyGramAccount business = new MoneyGramAccount();
        business.init();
        CountryUtil.findCountryByCode(rest.countryCode).ifPresent(business::setCountry);
        business.setEmail(rest.email);
        business.setFullName(rest.holderName);
        business.setState(rest.state);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public MoneyGramPaymentAccount toRestModel(MoneyGramAccount business) {
        MoneyGramPaymentAccount rest = toRestModel((MoneyGramAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public MoneyGramPaymentAccount toRestModel(MoneyGramAccountPayload business) {
        MoneyGramPaymentAccount rest = new MoneyGramPaymentAccount();
        rest.countryCode = business.getCountryCode();
        rest.email = business.getEmail();
        rest.holderName = business.getHolderName();
        rest.state = business.getState();
        toRestModel(rest, business);
        return rest;
    }

}
