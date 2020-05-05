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
public final class PicPayAccountPayload extends PaymentAccountPayload {
    private String accountName = "";

    public PicPayAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PicPayAccountPayload(String paymentMethod,
                                String id,
                                String accountName,
                                long maxTradePeriod,
                                Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.accountName = accountName;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setPicPayAccountPayload(protobuf.PicPayAccountPayload.newBuilder()
                        .setAccountName(accountName))
                .build();
    }

    public static PicPayAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        return new PicPayAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getPicPayAccountPayload().getAccountName(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + Res.getWithCol("payment.account.no") + " " + accountName;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.getWithCol("payment.account.no") + " " + accountName;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        return super.getAgeWitnessInputData(accountName.getBytes(StandardCharsets.UTF_8));
    }
}