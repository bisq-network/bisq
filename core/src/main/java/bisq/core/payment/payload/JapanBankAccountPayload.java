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

import bisq.core.locale.Res;

import com.google.protobuf.Message;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class JapanBankAccountPayload extends PaymentAccountPayload implements PayloadWithHolderName {
    // bank
    private String bankName = "";
    private String bankCode = "";
    // branch
    private String bankBranchName = "";
    private String bankBranchCode = "";
    // account
    private String bankAccountType = "";
    private String bankAccountName = "";
    private String bankAccountNumber = "";

    public JapanBankAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private JapanBankAccountPayload(String paymentMethod,
                                    String id,
                                    String bankName,
                                    String bankCode,
                                    String bankBranchName,
                                    String bankBranchCode,
                                    String bankAccountType,
                                    String bankAccountName,
                                    String bankAccountNumber,
                                    long maxTradePeriod,
                                    Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.bankName = bankName;
        this.bankCode = bankCode;
        this.bankBranchName = bankBranchName;
        this.bankBranchCode = bankBranchCode;
        this.bankAccountType = bankAccountType;
        this.bankAccountName = bankAccountName;
        this.bankAccountNumber = bankAccountNumber;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setJapanBankAccountPayload(
                        protobuf.JapanBankAccountPayload.newBuilder()
                                .setBankName(bankName)
                                .setBankCode(bankCode)
                                .setBankBranchName(bankBranchName)
                                .setBankBranchCode(bankBranchCode)
                                .setBankAccountType(bankAccountType)
                                .setBankAccountName(bankAccountName)
                                .setBankAccountNumber(bankAccountNumber)
                ).build();
    }

    public static JapanBankAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.JapanBankAccountPayload japanBankAccountPayload = proto.getJapanBankAccountPayload();
        return new JapanBankAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                japanBankAccountPayload.getBankName(),
                japanBankAccountPayload.getBankCode(),
                japanBankAccountPayload.getBankBranchName(),
                japanBankAccountPayload.getBankBranchCode(),
                japanBankAccountPayload.getBankAccountType(),
                japanBankAccountPayload.getBankAccountName(),
                japanBankAccountPayload.getBankAccountNumber(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.get("payment.japan.bank") + ": " + bankName + "(" + bankCode + ")\n" +
                Res.get("payment.japan.branch") + ": " + bankBranchName + "(" + bankBranchCode + ")\n" +
                Res.get("payment.japan.account") + ": " + bankAccountType + " " + bankAccountNumber + "\n" +
                Res.get("payment.japan.recipient") + ": " + bankAccountName;
    }


    @Override
    public byte[] getAgeWitnessInputData() {
        String all = this.bankName + this.bankBranchName + this.bankAccountType + this.bankAccountNumber + this.bankAccountName;
        return super.getAgeWitnessInputData(all.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getHolderName() {
        return bankAccountName;
    }
}
