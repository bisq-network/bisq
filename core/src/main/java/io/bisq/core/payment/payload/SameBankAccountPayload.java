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

package io.bisq.core.payment.payload;

import com.google.protobuf.Message;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@ToString
@Slf4j
public final class SameBankAccountPayload extends BankAccountPayload {

    public SameBankAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SameBankAccountPayload(String paymentMethodName,
                                   String id,
                                   String countryCode,
                                   String holderName,
                                   String bankName,
                                   String branchId,
                                   String accountNr,
                                   String accountType,
                                   String holderTaxId,
                                   String bankId,
                                   long maxTradePeriod,
                                   @Nullable Map<String, String> excludeFromJsonDataMap) {
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
                maxTradePeriod,
                excludeFromJsonDataMap);

    }

    @Override
    public Message toProtoMessage() {
        PB.BankAccountPayload.Builder bankAccountPayloadBuilder = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .getBankAccountPayloadBuilder()
                .setSameBankAccontPayload(PB.SameBankAccountPayload.newBuilder());

        PB.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayloadBuilder = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setBankAccountPayload(bankAccountPayloadBuilder);

        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayloadBuilder)
                .build();
    }

    public static SameBankAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        PB.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        PB.BankAccountPayload bankAccountPayload = countryBasedPaymentAccountPayload.getBankAccountPayload();
        return new SameBankAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                bankAccountPayload.getHolderName(),
                bankAccountPayload.getBankName().isEmpty() ? null : bankAccountPayload.getBankName(),
                bankAccountPayload.getBranchId().isEmpty() ? null : bankAccountPayload.getBranchId(),
                bankAccountPayload.getAccountNr().isEmpty() ? null : bankAccountPayload.getAccountNr(),
                bankAccountPayload.getAccountType().isEmpty() ? null : bankAccountPayload.getAccountType(),
                bankAccountPayload.getHolderTaxId().isEmpty() ? null : bankAccountPayload.getHolderTaxId(),
                bankAccountPayload.getBankId().isEmpty() ? null : bankAccountPayload.getBankId(),
                proto.getMaxTradePeriod(),
                CollectionUtils.isEmpty(proto.getExcludeFromJsonDataMap()) ? null : new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return "Transfer with same Bank - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }
}
