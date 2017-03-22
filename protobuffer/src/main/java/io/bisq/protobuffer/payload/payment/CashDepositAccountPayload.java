/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.protobuffer.payload.payment;

import io.bisq.common.app.Version;
import io.bisq.common.locale.BankUtil;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Locale;

@Setter
@EqualsAndHashCode(callSuper = true)
@ToString
@Slf4j
public class CashDepositAccountPayload extends CountryBasedPaymentAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    @Getter
    protected String holderName;
    @Getter
    protected String holderEmail;
    @Getter
    protected String bankName;
    @Getter
    protected String branchId;
    @Getter
    protected String accountNr;
    @Getter
    protected String accountType;
    @Nullable
    @Getter
    protected String requirements;
    @Nullable
    @Getter
    protected String holderTaxId;
    // Custom getter
    protected String bankId;

    public CashDepositAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    @Override
    public String getPaymentDetails(Locale locale) {
        return "Cash deposit - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup(Locale locale) {
        String bankName = BankUtil.isBankNameRequired(countryCode) ?
                BankUtil.getBankNameLabel(countryCode) + " " + this.bankName + "\n" : "";
        String bankId = BankUtil.isBankIdRequired(countryCode) ?
                BankUtil.getBankIdLabel(countryCode) + " " + this.bankId + "\n" : "";
        String branchId = BankUtil.isBranchIdRequired(countryCode) ?
                BankUtil.getBranchIdLabel(countryCode) + " " + this.branchId + "\n" : "";
        String accountNr = BankUtil.isAccountNrRequired(countryCode) ?
                BankUtil.getAccountNrLabel(countryCode) + " " + this.accountNr + "\n" : "";
        String accountType = BankUtil.isAccountTypeRequired(countryCode) ?
                BankUtil.getAccountTypeLabel(countryCode) + " " + this.accountType + "\n" : "";
        String holderIdString = BankUtil.isHolderIdRequired(countryCode) ?
                (BankUtil.getHolderIdLabel(countryCode) + " " + holderTaxId + "\n") : "";
        String requirementsString = requirements != null && !requirements.isEmpty() ?
                ("Extra requirements: " + requirements + "\n") : "";

        return "Holder name: " + holderName + "\n" +
                "Holder email: " + holderEmail + "\n" +
                bankName +
                bankId +
                branchId +
                accountNr +
                accountType +
                holderIdString +
                requirementsString +
                "Country of bank: " + new Locale(locale.getLanguage(), countryCode).getDisplayCountry();
    }

    @Override
    public PB.PaymentAccountPayload toProto() {
        PB.CashDepositAccountPayload.Builder cashDepositAccountPayload =
                PB.CashDepositAccountPayload.newBuilder()
                        .setHolderName(holderName)
                        .setHolderEmail(holderEmail)
                        .setBankName(bankName)
                        .setBankId(bankId)
                        .setBranchId(branchId)
                        .setAccountNr(accountNr)
                        .setRequirements(requirements)
                        .setHolderTaxId(holderTaxId);
        PB.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload =
                PB.CountryBasedPaymentAccountPayload.newBuilder()
                        .setCountryCode(countryCode)
                        .setCashDepositAccountPayload(cashDepositAccountPayload);
        PB.PaymentAccountPayload.Builder paymentAccountPayload =
                PB.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload);
        return paymentAccountPayload.build();
    }


    protected String getHolderIdLabel() {
        return BankUtil.getHolderIdLabel(countryCode);
    }

    @Nullable
    public String getBankId() {
        return BankUtil.isBankIdRequired(countryCode) ? bankId : bankName;
    }


}
