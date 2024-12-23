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

import org.apache.commons.lang3.ArrayUtils;

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
public final class SbpAccountPayload extends PaymentAccountPayload implements PayloadWithHolderName {
    private String holderName = "";
    private String mobileNumber = "";
    private String bankName = "";

    public SbpAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SbpAccountPayload(String paymentMethod,
                                       String id,
                                       String holderName,
                                       String mobileNumber,
                                       String bankName,
                                       long maxTradePeriod,
                                       Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.holderName = holderName;
        this.mobileNumber = mobileNumber;
        this.bankName = bankName;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setSbpAccountPayload(protobuf.SbpAccountPayload.newBuilder()
                        .setHolderName(holderName)
                        .setMobileNumber(mobileNumber)
                        .setBankName(bankName))
                .build();
    }

    public static SbpAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        return new SbpAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getSbpAccountPayload().getHolderName(),
                proto.getSbpAccountPayload().getMobileNumber(),
                proto.getSbpAccountPayload().getBankName(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " +
                Res.getWithCol("payment.account.owner.sbp") + " " + holderName + ", " +
                Res.getWithCol("payment.mobile") + " " + mobileNumber + ", " +
                Res.getWithCol("payment.bank.name") + " " + bankName;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.getWithCol("payment.account.owner.sbp") + " " + holderName + "\n" +
                Res.getWithCol("payment.mobile") + " " + mobileNumber + "\n" +
                Res.getWithCol("payment.bank.name") + " " + bankName;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // We don't add holderName because we don't want to break age validation if the user recreates an account with
        // slight changes in holder name (e.g. add or remove middle name)
        return super.getAgeWitnessInputData(
            ArrayUtils.addAll(
                mobileNumber.getBytes(StandardCharsets.UTF_8),
                bankName.getBytes(StandardCharsets.UTF_8)
                )
            );
    }

    @Override
    public String getOwnerId() {
        return holderName;
    }
}
