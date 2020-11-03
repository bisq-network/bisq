/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.payment.payload;

import bisq.core.locale.BankUtil;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;

import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
@Setter
@Getter
@ToString
@Slf4j
public abstract class BankAccountPayload extends CountryBasedPaymentAccountPayload implements PayloadWithHolderName {
    protected String holderName = "";
    @Nullable
    protected String bankName;
    @Nullable
    protected String branchId;
    @Nullable
    protected String accountNr;
    @Nullable
    protected String accountType;
    @Nullable
    protected String holderTaxId;
    @Nullable
    protected String bankId;
    @Nullable
    protected String nationalAccountId;

    protected BankAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected BankAccountPayload(String paymentMethodName,
                                 String id,
                                 String countryCode,
                                 String holderName,
                                 @Nullable String bankName,
                                 @Nullable String branchId,
                                 @Nullable String accountNr,
                                 @Nullable String accountType,
                                 @Nullable String holderTaxId,
                                 @Nullable String bankId,
                                 @Nullable String nationalAccountId,
                                 long maxTradePeriod,
                                 Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                countryCode,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.holderName = holderName;
        this.bankName = bankName;
        this.branchId = branchId;
        this.accountNr = accountNr;
        this.accountType = accountType;
        this.holderTaxId = holderTaxId;
        this.bankId = bankId;
        this.nationalAccountId = nationalAccountId;
    }

    @Override
    public protobuf.PaymentAccountPayload.Builder getPaymentAccountPayloadBuilder() {
        protobuf.BankAccountPayload.Builder builder =
                protobuf.BankAccountPayload.newBuilder()
                        .setHolderName(holderName);
        Optional.ofNullable(holderTaxId).ifPresent(builder::setHolderTaxId);
        Optional.ofNullable(bankName).ifPresent(builder::setBankName);
        Optional.ofNullable(branchId).ifPresent(builder::setBranchId);
        Optional.ofNullable(accountNr).ifPresent(builder::setAccountNr);
        Optional.ofNullable(accountType).ifPresent(builder::setAccountType);
        Optional.ofNullable(bankId).ifPresent(builder::setBankId);
        Optional.ofNullable(nationalAccountId).ifPresent(builder::setNationalAccountId);
        final protobuf.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayloadBuilder = super.getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setBankAccountPayload(builder);
        return super.getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayloadBuilder);
    }


    @Override
    public String getPaymentDetails() {
        return "Bank account transfer - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        String bankName = BankUtil.isBankNameRequired(countryCode) ?
                BankUtil.getBankNameLabel(countryCode) + ": " + this.bankName + "\n" : "";
        String bankId = BankUtil.isBankIdRequired(countryCode) ?
                BankUtil.getBankIdLabel(countryCode) + ": " + this.bankId + "\n" : "";
        String branchId = BankUtil.isBranchIdRequired(countryCode) ?
                BankUtil.getBranchIdLabel(countryCode) + ": " + this.branchId + "\n" : "";
        String nationalAccountId = BankUtil.isNationalAccountIdRequired(countryCode) ?
                BankUtil.getNationalAccountIdLabel(countryCode) + ": " + this.nationalAccountId + "\n" : "";
        String accountNr = BankUtil.isAccountNrRequired(countryCode) ?
                BankUtil.getAccountNrLabel(countryCode) + ": " + this.accountNr + "\n" : "";
        String accountType = BankUtil.isAccountTypeRequired(countryCode) ?
                BankUtil.getAccountTypeLabel(countryCode) + ": " + this.accountType + "\n" : "";
        String holderTaxIdString = BankUtil.isHolderIdRequired(countryCode) ?
                (BankUtil.getHolderIdLabel(countryCode) + ": " + holderTaxId + "\n") : "";

        return Res.getWithCol("payment.account.owner") + " " + holderName + "\n" +
                bankName +
                bankId +
                branchId +
                nationalAccountId +
                accountNr +
                accountType +
                holderTaxIdString +
                Res.getWithCol("payment.bank.country") + " " + CountryUtil.getNameByCode(countryCode);
    }

    protected String getHolderIdLabel() {
        return BankUtil.getHolderIdLabel(countryCode);
    }

    @Nullable
    public String getBankId() {
        return BankUtil.isBankIdRequired(countryCode) ? bankId : bankName;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        String bankName = BankUtil.isBankNameRequired(countryCode) ? this.bankName : "";
        String bankId = BankUtil.isBankIdRequired(countryCode) ? this.bankId : "";
        String branchId = BankUtil.isBranchIdRequired(countryCode) ? this.branchId : "";
        String accountNr = BankUtil.isAccountNrRequired(countryCode) ? this.accountNr : "";
        String accountType = BankUtil.isAccountTypeRequired(countryCode) ? this.accountType : "";
        String holderTaxIdString = BankUtil.isHolderIdRequired(countryCode) ?
                (BankUtil.getHolderIdLabel(countryCode) + " " + holderTaxId + "\n") : "";
        String nationalAccountId = BankUtil.isNationalAccountIdRequired(countryCode) ? this.nationalAccountId : "";

        // We don't add holderName because we don't want to break age validation if the user recreates an account with
        // slight changes in holder name (e.g. add or remove middle name)

        String all = bankName +
                bankId +
                branchId +
                accountNr +
                accountType +
                holderTaxIdString +
                nationalAccountId;

        return super.getAgeWitnessInputData(all.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getOwnerId() {
        return holderName;
    }
}
