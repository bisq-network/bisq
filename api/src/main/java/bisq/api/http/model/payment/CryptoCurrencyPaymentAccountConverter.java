package bisq.api.http.model.payment;

import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.payload.CryptoCurrencyAccountPayload;

public class CryptoCurrencyPaymentAccountConverter extends AbstractPaymentAccountConverter<CryptoCurrencyAccount, CryptoCurrencyAccountPayload, CryptoCurrencyPaymentAccount> {

    @Override
    public CryptoCurrencyAccount toBusinessModel(CryptoCurrencyPaymentAccount rest) {
        CryptoCurrencyAccount business = new CryptoCurrencyAccount();
        business.init();
        business.setAddress(rest.address);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public CryptoCurrencyPaymentAccount toRestModel(CryptoCurrencyAccount business) {
        CryptoCurrencyPaymentAccount rest = toRestModel((CryptoCurrencyAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public CryptoCurrencyPaymentAccount toRestModel(CryptoCurrencyAccountPayload business) {
        CryptoCurrencyPaymentAccount rest = new CryptoCurrencyPaymentAccount();
        rest.address = business.getAddress();
        toRestModel(rest, business);
        return rest;
    }

}
