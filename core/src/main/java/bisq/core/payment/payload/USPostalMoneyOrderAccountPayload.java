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

import org.springframework.util.CollectionUtils;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.charset.Charset;

import java.util.HashMap;
import java.util.Map;

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
public final class USPostalMoneyOrderAccountPayload extends PaymentAccountPayload {
    private String postalAddress = "";
    private String holderName = "";

    public USPostalMoneyOrderAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private USPostalMoneyOrderAccountPayload(String paymentMethod, String id,
                                             String postalAddress,
                                             String holderName,
                                             long maxTradePeriod,
                                             @Nullable Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.postalAddress = postalAddress;
        this.holderName = holderName;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setUSPostalMoneyOrderAccountPayload(protobuf.USPostalMoneyOrderAccountPayload.newBuilder()
                        .setPostalAddress(postalAddress)
                        .setHolderName(holderName))
                .build();
    }

    public static USPostalMoneyOrderAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        return new USPostalMoneyOrderAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getUSPostalMoneyOrderAccountPayload().getPostalAddress(),
                proto.getUSPostalMoneyOrderAccountPayload().getHolderName(),
                proto.getMaxTradePeriod(),
                CollectionUtils.isEmpty(proto.getExcludeFromJsonDataMap()) ? null : new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + Res.getWithCol("payment.account.owner") + " " + holderName + ", " +
                Res.getWithCol("payment.postal.address") + " " + postalAddress;
    }


    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.getWithCol("payment.account.owner") + " " + holderName + "\n" +
                Res.getWithCol("payment.postal.address") + " " + postalAddress;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // We use here the holderName because the address alone seems to be too weak
        return super.getAgeWitnessInputData(ArrayUtils.addAll(holderName.getBytes(Charset.forName("UTF-8")),
                postalAddress.getBytes(Charset.forName("UTF-8"))));
    }
}
