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

import com.google.protobuf.Message;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public class CashDepositAccountPayload extends CountryBasedPaymentAccountPayload implements PayloadWithHolderName {
    private String holderName = "";
    @Nullable
    private String holderEmail;
    @Nullable
    private String bankName;
    @Nullable
    private String branchId;
    @Nullable
    private String accountNr;
    @Nullable
    private String accountType;
    @Nullable
    private String requirements;
    @Nullable
    private String holderTaxId;
    @Nullable
    private String bankId;
    @Nullable
    protected String nationalAccountId;

    public CashDepositAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CashDepositAccountPayload(String paymentMethodName,
                                      String id,
                                      String countryCode,
                                      String holderName,
                                      @Nullable String holderEmail,
                                      @Nullable String bankName,
                                      @Nullable String branchId,
                                      @Nullable String accountNr,
                                      @Nullable String accountType,
                                      @Nullable String requirements,
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
        this.holderEmail = holderEmail;
        this.bankName = bankName;
        this.branchId = branchId;
        this.accountNr = accountNr;
        this.accountType = accountType;
        this.requirements = requirements;
        this.holderTaxId = holderTaxId;
        this.bankId = bankId;
        this.nationalAccountId = nationalAccountId;
    }

    @Override
    public Message toProtoMessage() {
        protobuf.CashDepositAccountPayload.Builder builder =
                protobuf.CashDepositAccountPayload.newBuilder()
                        .setHolderName(holderName);
        Optional.ofNullable(holderEmail).ifPresent(builder::setHolderEmail);
        Optional.ofNullable(bankName).ifPresent(builder::setBankName);
        Optional.ofNullable(branchId).ifPresent(builder::setBranchId);
        Optional.ofNullable(accountNr).ifPresent(builder::setAccountNr);
        Optional.ofNullable(accountType).ifPresent(builder::setAccountType);
        Optional.ofNullable(requirements).ifPresent(builder::setRequirements);
        Optional.ofNullable(holderTaxId).ifPresent(builder::setHolderTaxId);
        Optional.ofNullable(bankId).ifPresent(builder::setBankId);
        Optional.ofNullable(nationalAccountId).ifPresent(builder::setNationalAccountId);

        final protobuf.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setCashDepositAccountPayload(builder);
        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload)
                .build();
    }

    public static PaymentAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        protobuf.CashDepositAccountPayload cashDepositAccountPayload = countryBasedPaymentAccountPayload.getCashDepositAccountPayload();
        return new CashDepositAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                cashDepositAccountPayload.getHolderName(),
                cashDepositAccountPayload.getHolderEmail().isEmpty() ? null : cashDepositAccountPayload.getHolderEmail(),
                cashDepositAccountPayload.getBankName().isEmpty() ? null : cashDepositAccountPayload.getBankName(),
                cashDepositAccountPayload.getBranchId().isEmpty() ? null : cashDepositAccountPayload.getBranchId(),
                cashDepositAccountPayload.getAccountNr().isEmpty() ? null : cashDepositAccountPayload.getAccountNr(),
                cashDepositAccountPayload.getAccountType().isEmpty() ? null : cashDepositAccountPayload.getAccountType(),
                cashDepositAccountPayload.getRequirements().isEmpty() ? null : cashDepositAccountPayload.getRequirements(),
                cashDepositAccountPayload.getHolderTaxId().isEmpty() ? null : cashDepositAccountPayload.getHolderTaxId(),
                cashDepositAccountPayload.getBankId().isEmpty() ? null : cashDepositAccountPayload.getBankId(),
                cashDepositAccountPayload.getNationalAccountId().isEmpty() ? null : cashDepositAccountPayload.getNationalAccountId(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return "Cash deposit - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
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
        String requirementsString = requirements != null && !requirements.isEmpty() ?
                (Res.getWithCol("payment.extras") + " " + requirements + "\n") : "";
        String emailString = holderEmail != null ?
                (Res.getWithCol("payment.email") + " " + holderEmail + "\n") : "";

        return Res.getWithCol("payment.account.owner") + " " + holderName + "\n" +
                emailString +
                bankName +
                bankId +
                branchId +
                nationalAccountId +
                accountNr +
                accountType +
                holderTaxIdString +
                requirementsString +
                Res.getWithCol("payment.bank.country") + " " + CountryUtil.getNameByCode(countryCode);
    }

    public String getHolderIdLabel() {
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

        // We don't add holderName and holderEmail because we don't want to break age validation if the user recreates an account with
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
