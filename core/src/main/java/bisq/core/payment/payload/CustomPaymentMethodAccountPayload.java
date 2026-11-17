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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * The account data of the custom payment method. Both fields are free texts of the account owner.
 * A payload of a peer is not checked when it is read, so its texts have to be checked with
 * {@link bisq.core.payment.CustomPaymentMethodUtil} before they are used.
 */
@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class CustomPaymentMethodAccountPayload extends PaymentAccountPayload {
    private String paymentMethodName = "";
    private String accountData = "";

    public CustomPaymentMethodAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CustomPaymentMethodAccountPayload(String paymentMethod,
                                              String id,
                                              String paymentMethodName,
                                              String accountData,
                                              long maxTradePeriod,
                                              PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.paymentMethodName = paymentMethodName;
        this.accountData = accountData;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setCustomPaymentMethodAccountPayload(protobuf.CustomPaymentMethodAccountPayload.newBuilder()
                        .setPaymentMethodName(paymentMethodName)
                        .setAccountData(accountData))
                .build();
    }

    @SuppressWarnings("deprecation")
    public static CustomPaymentMethodAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        return new CustomPaymentMethodAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getCustomPaymentMethodAccountPayload().getPaymentMethodName(),
                proto.getCustomPaymentMethodAccountPayload().getAccountData(),
                proto.getMaxTradePeriod(),
                new PaymentAccountPayloadExcludeFromJsonMap(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // The payment details are shown in one line
    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " +
                Res.getWithCol("payment.custom.name") + " " + paymentMethodName + ", " +
                Res.getWithCol("payment.custom.accountData") + " " + accountData.replaceAll("\\R+", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.getWithCol("payment.custom.name") + " " + paymentMethodName + "\n" +
                Res.getWithCol("payment.custom.accountData") + " " + accountData;
    }

    // The same data as Bisq 2 uses for its user defined account, so that an account age can be verified there
    @Override
    public byte[] getAgeWitnessInputData() {
        return super.getAgeWitnessInputData(accountData.getBytes(StandardCharsets.UTF_8));
    }
}
