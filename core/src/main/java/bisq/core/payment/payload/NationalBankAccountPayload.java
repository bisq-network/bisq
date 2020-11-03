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

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@ToString
@Slf4j
public final class NationalBankAccountPayload extends BankAccountPayload {

    public NationalBankAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private NationalBankAccountPayload(String paymentMethodName,
                                       String id,
                                       String countryCode,
                                       String holderName,
                                       String bankName,
                                       String branchId,
                                       String accountNr,
                                       String accountType,
                                       String holderTaxId,
                                       String bankId,
                                       String nationalAccountId,
                                       long maxTradePeriod,
                                       Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                countryCode,
                holderName,
                bankName,
                branchId,
                accountNr,
                accountType,
                holderTaxId,
                bankId,
                nationalAccountId,
                maxTradePeriod,
                excludeFromJsonDataMap);
    }

    @Override
    public Message toProtoMessage() {
        protobuf.BankAccountPayload.Builder bankAccountPayloadBuilder = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .getBankAccountPayloadBuilder()
                .setNationalBankAccountPayload(protobuf.NationalBankAccountPayload.newBuilder());

        protobuf.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayloadBuilder = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setBankAccountPayload(bankAccountPayloadBuilder);

        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayloadBuilder)
                .build();
    }

    public static NationalBankAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        protobuf.BankAccountPayload bankAccountPayloadPB = countryBasedPaymentAccountPayload.getBankAccountPayload();
        return new NationalBankAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                bankAccountPayloadPB.getHolderName(),
                bankAccountPayloadPB.getBankName().isEmpty() ? null : bankAccountPayloadPB.getBankName(),
                bankAccountPayloadPB.getBranchId().isEmpty() ? null : bankAccountPayloadPB.getBranchId(),
                bankAccountPayloadPB.getAccountNr().isEmpty() ? null : bankAccountPayloadPB.getAccountNr(),
                bankAccountPayloadPB.getAccountType().isEmpty() ? null : bankAccountPayloadPB.getAccountType(),
                bankAccountPayloadPB.getHolderTaxId().isEmpty() ? null : bankAccountPayloadPB.getHolderTaxId(),
                bankAccountPayloadPB.getBankId().isEmpty() ? null : bankAccountPayloadPB.getBankId(),
                bankAccountPayloadPB.getNationalAccountId().isEmpty() ? null : bankAccountPayloadPB.getNationalAccountId(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }
}
