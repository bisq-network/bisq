package io.bisq.api.model.payment;

import io.bisq.core.payment.CashDepositAccount;
import io.bisq.core.payment.payload.CashDepositAccountPayload;

public class CashDepositPaymentAccountConverter extends AbstractPaymentAccountConverter<CashDepositAccount, CashDepositPaymentAccount> {

    @Override
    public CashDepositAccount toBusinessModel(CashDepositPaymentAccount rest) {
        final CashDepositAccount business = new CashDepositAccount();
        business.init();
        business.setRequirements(rest.requirements);
        final CashDepositAccountPayload paymentAccountPayload = (CashDepositAccountPayload) business.getPaymentAccountPayload();
        paymentAccountPayload.setAccountNr(rest.accountNr);
        paymentAccountPayload.setAccountType(rest.accountType);
        paymentAccountPayload.setBankId(rest.bankId);
        paymentAccountPayload.setBankName(rest.bankName);
        paymentAccountPayload.setBranchId(rest.branchId);
        paymentAccountPayload.setCountryCode(rest.countryCode);
        paymentAccountPayload.setHolderEmail(rest.holderEmail);
        paymentAccountPayload.setHolderName(rest.holderName);
        paymentAccountPayload.setHolderTaxId(rest.holderTaxId);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public CashDepositPaymentAccount toRestModel(CashDepositAccount business) {
        final CashDepositPaymentAccount rest = new CashDepositPaymentAccount();
        final CashDepositAccountPayload payload = (CashDepositAccountPayload) business.getPaymentAccountPayload();
        rest.requirements = business.getRequirements();
        rest.accountNr = payload.getAccountNr();
        rest.accountType = payload.getAccountType();
        rest.bankId = payload.getBankId();
        rest.bankName = payload.getBankName();
        rest.branchId = payload.getBranchId();
        rest.countryCode = payload.getCountryCode();
        rest.holderEmail = payload.getHolderEmail();
        rest.holderName = payload.getHolderName();
        rest.holderTaxId = payload.getHolderTaxId();
        toRestModel(rest, business);
        return rest;
    }

}
