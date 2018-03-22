package io.bisq.api.model.payment;

import io.bisq.core.payment.SameBankAccount;
import io.bisq.core.payment.payload.SameBankAccountPayload;

public class SameBankAccountPaymentAccountConverter extends AbstractPaymentAccountConverter<SameBankAccount, SameBankAccountPaymentAccount> {

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
        final SameBankAccountPaymentAccount rest = new SameBankAccountPaymentAccount();
        final SameBankAccountPayload payload = (SameBankAccountPayload) business.getPaymentAccountPayload();
        rest.accountNr = payload.getAccountNr();
        rest.accountType = payload.getAccountType();
        rest.bankId = payload.getBankId();
        rest.bankName = payload.getBankName();
        rest.branchId = payload.getBranchId();
        rest.countryCode = payload.getCountryCode();
        rest.holderName = payload.getHolderName();
        rest.holderTaxId = payload.getHolderTaxId();
        toRestModel(rest, business);
        return rest;
    }

}
