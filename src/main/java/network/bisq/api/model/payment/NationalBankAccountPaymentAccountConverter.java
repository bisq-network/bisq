package network.bisq.api.model.payment;

import bisq.core.payment.NationalBankAccount;
import bisq.core.payment.payload.NationalBankAccountPayload;

public class NationalBankAccountPaymentAccountConverter extends AbstractPaymentAccountConverter<NationalBankAccount, NationalBankAccountPayload, NationalBankAccountPaymentAccount> {

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
        final NationalBankAccountPaymentAccount rest = toRestModel((NationalBankAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public NationalBankAccountPaymentAccount toRestModel(NationalBankAccountPayload business) {
        final NationalBankAccountPaymentAccount rest = new NationalBankAccountPaymentAccount();
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
