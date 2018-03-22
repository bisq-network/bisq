package io.bisq.api.model.payment;

import io.bisq.core.payment.NationalBankAccount;
import io.bisq.core.payment.payload.NationalBankAccountPayload;

public class NationalBankAccountPaymentAccountConverter extends AbstractPaymentAccountConverter<NationalBankAccount, NationalBankAccountPaymentAccount> {

    @Override
    public NationalBankAccount toBusinessModel(NationalBankAccountPaymentAccount rest) {
        final NationalBankAccount business = new NationalBankAccount();
        business.init();
        final NationalBankAccountPayload paymentAccountPayload = (NationalBankAccountPayload) business.getPaymentAccountPayload();
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
    public NationalBankAccountPaymentAccount toRestModel(NationalBankAccount business) {
        final NationalBankAccountPaymentAccount rest = new NationalBankAccountPaymentAccount();
        final NationalBankAccountPayload payload = (NationalBankAccountPayload) business.getPaymentAccountPayload();
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
