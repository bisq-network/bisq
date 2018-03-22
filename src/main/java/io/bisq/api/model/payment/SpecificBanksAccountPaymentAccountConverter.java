package io.bisq.api.model.payment;

import io.bisq.core.payment.SpecificBanksAccount;
import io.bisq.core.payment.payload.SpecificBanksAccountPayload;

import java.util.List;

public class SpecificBanksAccountPaymentAccountConverter extends AbstractPaymentAccountConverter<SpecificBanksAccount, SpecificBanksAccountPaymentAccount> {

    @Override
    public SpecificBanksAccount toBusinessModel(SpecificBanksAccountPaymentAccount rest) {
        final SpecificBanksAccount business = new SpecificBanksAccount();
        business.init();
        final SpecificBanksAccountPayload paymentAccountPayload = (SpecificBanksAccountPayload) business.getPaymentAccountPayload();
        paymentAccountPayload.setAccountNr(rest.accountNr);
        paymentAccountPayload.setAccountType(rest.accountType);
        paymentAccountPayload.setBankId(rest.bankId);
        paymentAccountPayload.setBankName(rest.bankName);
        paymentAccountPayload.setBranchId(rest.branchId);
        paymentAccountPayload.setCountryCode(rest.countryCode);
        paymentAccountPayload.setHolderName(rest.holderName);
        paymentAccountPayload.setHolderTaxId(rest.holderTaxId);
        rest.acceptedBanks.stream().forEach(paymentAccountPayload::addAcceptedBank);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public SpecificBanksAccountPaymentAccount toRestModel(SpecificBanksAccount business) {
        final SpecificBanksAccountPaymentAccount rest = new SpecificBanksAccountPaymentAccount();
        final SpecificBanksAccountPayload payload = (SpecificBanksAccountPayload) business.getPaymentAccountPayload();
        rest.accountNr = payload.getAccountNr();
        rest.accountType = payload.getAccountType();
        rest.bankId = payload.getBankId();
        rest.bankName = payload.getBankName();
        rest.branchId = payload.getBranchId();
        rest.countryCode = payload.getCountryCode();
        rest.holderName = payload.getHolderName();
        rest.holderTaxId = payload.getHolderTaxId();
        final List<String> acceptedBanks = business.getAcceptedBanks();
        if (null != acceptedBanks)
            rest.acceptedBanks.addAll(acceptedBanks);
        toRestModel(rest, business);
        return rest;
    }

}
