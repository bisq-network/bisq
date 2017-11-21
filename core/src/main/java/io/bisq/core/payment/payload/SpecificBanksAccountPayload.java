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

import com.google.common.base.Joiner;
import com.google.protobuf.Message;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
@Slf4j
public final class SpecificBanksAccountPayload extends BankAccountPayload {
    // Dont use a set here as we need a deterministic ordering, otherwise the contract hash does not match
    private ArrayList<String> acceptedBanks = new ArrayList<>();

    public SpecificBanksAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SpecificBanksAccountPayload(String paymentMethodName,
                                        String id,
                                        String countryCode,
                                        String holderName,
                                        String bankName,
                                        String branchId,
                                        String accountNr,
                                        String accountType,
                                        String holderTaxId,
                                        String bankId,
                                        ArrayList<String> acceptedBanks,
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

        this.acceptedBanks = acceptedBanks;
    }

    @Override
    public Message toProtoMessage() {
        final PB.SpecificBanksAccountPayload.Builder builder = PB.SpecificBanksAccountPayload.newBuilder()
                .addAllAcceptedBanks(acceptedBanks);

        PB.BankAccountPayload.Builder bankAccountPayloadBuilder = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .getBankAccountPayloadBuilder()
                .setSpecificBanksAccountPayload(builder);

        PB.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayloadBuilder = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setBankAccountPayload(bankAccountPayloadBuilder);

        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayloadBuilder)
                .build();
    }

    public static SpecificBanksAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        PB.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        PB.BankAccountPayload bankAccountPayload = countryBasedPaymentAccountPayload.getBankAccountPayload();
        PB.SpecificBanksAccountPayload specificBanksAccountPayload = bankAccountPayload.getSpecificBanksAccountPayload();
        return new SpecificBanksAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                bankAccountPayload.getHolderName(),
                bankAccountPayload.getBankName().isEmpty() ? null : bankAccountPayload.getBankName(),
                bankAccountPayload.getBranchId().isEmpty() ? null : bankAccountPayload.getBranchId(),
                bankAccountPayload.getAccountNr().isEmpty() ? null : bankAccountPayload.getAccountNr(),
                bankAccountPayload.getAccountType().isEmpty() ? null : bankAccountPayload.getAccountType(),
                bankAccountPayload.getHolderTaxId().isEmpty() ? null : bankAccountPayload.getHolderTaxId(),
                bankAccountPayload.getBankId().isEmpty() ? null : bankAccountPayload.getBankId(),
                new ArrayList<>(specificBanksAccountPayload.getAcceptedBanksList()),
                proto.getMaxTradePeriod(),
                CollectionUtils.isEmpty(proto.getExcludeFromJsonDataMap()) ? null : new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void clearAcceptedBanks() {
        acceptedBanks = new ArrayList<>();
    }

    public void addAcceptedBank(String bankName) {
        if (!acceptedBanks.contains(bankName))
            acceptedBanks.add(bankName);
    }

    @Override
    public String getPaymentDetails() {
        return "Transfers with specific banks - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return super.getPaymentDetailsForTradePopup() + "\n" +
                "Accepted banks: " + Joiner.on(", ").join(acceptedBanks);
    }
}
