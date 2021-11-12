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
import bisq.core.locale.Res;

import com.google.protobuf.Message;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
@Setter
@Slf4j
public final class DomesticWireTransferAccountPayload extends BankAccountPayload {
    private String holderAddress = "";

    public DomesticWireTransferAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DomesticWireTransferAccountPayload(String paymentMethodName,
                                      String id,
                                      String countryCode,
                                      String holderName,
                                      String bankName,
                                      String branchId,
                                      String accountNr,
                                      String holderAddress,
                                      long maxTradePeriod,
                                      Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                countryCode,
                holderName,
                bankName,
                branchId,
                accountNr,
                null,
                null,           // holderTaxId not used
                null,               // bankId not used
                null,       // nationalAccountId not used
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.holderAddress = holderAddress;
    }

    @Override
    public Message toProtoMessage() {
        protobuf.DomesticWireTransferAccountPayload.Builder builder = protobuf.DomesticWireTransferAccountPayload.newBuilder()
                .setHolderAddress(holderAddress);
        protobuf.BankAccountPayload.Builder bankAccountPayloadBuilder = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .getBankAccountPayloadBuilder()
                .setDomesticWireTransferAccountPayload(builder);
        protobuf.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayloadBuilder = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setBankAccountPayload(bankAccountPayloadBuilder);
        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayloadBuilder)
                .build();
    }

    public static DomesticWireTransferAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        protobuf.BankAccountPayload bankAccountPayloadPB = countryBasedPaymentAccountPayload.getBankAccountPayload();
        protobuf.DomesticWireTransferAccountPayload accountPayloadPB = bankAccountPayloadPB.getDomesticWireTransferAccountPayload();
        return new DomesticWireTransferAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                bankAccountPayloadPB.getHolderName(),
                bankAccountPayloadPB.getBankName().isEmpty() ? null : bankAccountPayloadPB.getBankName(),
                bankAccountPayloadPB.getBranchId().isEmpty() ? null : bankAccountPayloadPB.getBranchId(),
                bankAccountPayloadPB.getAccountNr().isEmpty() ? null : bankAccountPayloadPB.getAccountNr(),
                accountPayloadPB.getHolderAddress().isEmpty() ? null : accountPayloadPB.getHolderAddress(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }

    @Override
    public String getPaymentDetails() {
        String paymentDetails = (Res.get(paymentMethodId) + " - " +
                Res.getWithCol("payment.account.owner") + " " + holderName + ", " +
                BankUtil.getBankNameLabel(countryCode) + ": " + this.bankName + ", " +
                BankUtil.getBranchIdLabel(countryCode) + ": " + this.branchId + ", " +
                BankUtil.getAccountNrLabel(countryCode) + ": " + this.accountNr);
        return paymentDetails;
    }
}
