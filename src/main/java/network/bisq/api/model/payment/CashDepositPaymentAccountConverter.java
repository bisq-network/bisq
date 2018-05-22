package network.bisq.api.model.payment;

import bisq.core.payment.CashDepositAccount;
import bisq.core.payment.payload.CashDepositAccountPayload;

public class CashDepositPaymentAccountConverter extends AbstractPaymentAccountConverter<CashDepositAccount, CashDepositAccountPayload, CashDepositPaymentAccount> {

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
        final CashDepositPaymentAccount rest = toRestModel((CashDepositAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public CashDepositPaymentAccount toRestModel(CashDepositAccountPayload business) {
        final CashDepositPaymentAccount rest = new CashDepositPaymentAccount();
        rest.requirements = business.getRequirements();
        rest.accountNr = business.getAccountNr();
        rest.accountType = business.getAccountType();
        rest.bankId = business.getBankId();
        rest.bankName = business.getBankName();
        rest.branchId = business.getBranchId();
        rest.countryCode = business.getCountryCode();
        rest.holderEmail = business.getHolderEmail();
        rest.holderName = business.getHolderName();
        rest.holderTaxId = business.getHolderTaxId();
        toRestModel(rest, business);
        return rest;
    }

}
