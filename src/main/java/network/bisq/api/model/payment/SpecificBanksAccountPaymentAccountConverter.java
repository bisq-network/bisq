package network.bisq.api.model.payment;

import bisq.core.payment.SpecificBanksAccount;
import bisq.core.payment.payload.SpecificBanksAccountPayload;

import java.util.List;

public class SpecificBanksAccountPaymentAccountConverter extends AbstractPaymentAccountConverter<SpecificBanksAccount, SpecificBanksAccountPayload, SpecificBanksAccountPaymentAccount> {

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
        final SpecificBanksAccountPaymentAccount rest = toRestModel((SpecificBanksAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public SpecificBanksAccountPaymentAccount toRestModel(SpecificBanksAccountPayload business) {
        final SpecificBanksAccountPaymentAccount rest = new SpecificBanksAccountPaymentAccount();
        rest.accountNr = business.getAccountNr();
        rest.accountType = business.getAccountType();
        rest.bankId = business.getBankId();
        rest.bankName = business.getBankName();
        rest.branchId = business.getBranchId();
        rest.countryCode = business.getCountryCode();
        rest.holderName = business.getHolderName();
        rest.holderTaxId = business.getHolderTaxId();
        final List<String> acceptedBanks = business.getAcceptedBanks();
        if (null != acceptedBanks)
            rest.acceptedBanks.addAll(acceptedBanks);
        toRestModel(rest, business);
        return rest;

    }

}
