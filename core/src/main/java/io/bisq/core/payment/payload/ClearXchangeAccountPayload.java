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
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class ClearXchangeAccountPayload extends PaymentAccountPayload {
    private String emailOrMobileNr = "";
    private String holderName = "";

    public ClearXchangeAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ClearXchangeAccountPayload(String paymentMethod,
                                       String id,
                                       String emailOrMobileNr,
                                       String holderName,
                                       long maxTradePeriod,
                                       @Nullable Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.emailOrMobileNr = emailOrMobileNr;
        this.holderName = holderName;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setClearXchangeAccountPayload(PB.ClearXchangeAccountPayload.newBuilder()
                        .setEmailOrMobileNr(emailOrMobileNr)
                        .setHolderName(holderName))
                .build();
    }

    public static ClearXchangeAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        return new ClearXchangeAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getClearXchangeAccountPayload().getEmailOrMobileNr(),
                proto.getClearXchangeAccountPayload().getHolderName(),
                proto.getMaxTradePeriod(),
                CollectionUtils.isEmpty(proto.getExcludeFromJsonDataMap()) ? null : new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return "Zelle (ClearXchange) - Holder name: " + holderName + ", Email or mobile no.: " + emailOrMobileNr;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "Holder name: " + holderName + "\n" +
                "Email or mobile no.: " + emailOrMobileNr;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // We don't add holderName because we don't want to break age validation if the user recreates an account with
        // slight changes in holder name (e.g. add or remove middle name)
        return super.getAgeWitnessInputData(emailOrMobileNr.getBytes(Charset.forName("UTF-8")));
    }
}
