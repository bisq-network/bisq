package network.bisq.api.model.payment;

import bisq.core.payment.SameBankAccount;
import bisq.core.payment.payload.SameBankAccountPayload;

public class SameBankAccountPaymentAccountConverter extends AbstractPaymentAccountConverter<SameBankAccount, SameBankAccountPayload, SameBankAccountPaymentAccount> {

    @Override
    public SameBankAccount toBusinessModel(SameBankAccountPaymentAccount rest) {
        final SameBankAccount business = new SameBankAccount();
        business.init();
        final SameBankAccountPayload paymentAccountPayload = (SameBankAccountPayload) business.getPaymentAccountPayload();
        paymentAccountPayload.setAccountNr(rest.accountNr);
        paymentAccountPayload.setAccountType(rest.accountType);
        paymentAccountPayload.setBankId(rest.bankId);
        paymentAccountPayload.setBankName(rest.bankName);
        paymentAccountPayload.setBranchId(rest.branchId);
        paymentAccountPayload.setCountryCode(rest.countryCode);
        paymentAccountPayload.setHolderName(rest.holderName);
        paymentAccountPayload.setHolderTaxId(rest.holderTaxId);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public SameBankAccountPaymentAccount toRestModel(SameBankAccount business) {
        final SameBankAccountPaymentAccount rest = toRestModel((SameBankAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public SameBankAccountPaymentAccount toRestModel(SameBankAccountPayload business) {
        final SameBankAccountPaymentAccount rest = new SameBankAccountPaymentAccount();
        rest.accountNr = business.getAccountNr();
        rest.accountType = business.getAccountType();
        rest.bankId = business.getBankId();
        rest.bankName = business.getBankName();
        rest.branchId = business.getBranchId();
        rest.countryCode = business.getCountryCode();
        rest.holderName = business.getHolderName();
        rest.holderTaxId = business.getHolderTaxId();
        toRestModel(rest, business);
        return rest;
    }

}
