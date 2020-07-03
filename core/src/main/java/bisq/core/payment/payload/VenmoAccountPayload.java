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

// Cannot be deleted as it would break old trade history entries
// Removed due too high chargeback risk
@Deprecated
@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class VenmoAccountPayload extends PaymentAccountPayload {
    private String venmoUserName = "";
    private String holderName = "";

    public VenmoAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private VenmoAccountPayload(String paymentMethod,
                                String id,
                                String venmoUserName,
                                String holderName,
                                long maxTradePeriod,
                                Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.venmoUserName = venmoUserName;
        this.holderName = holderName;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setVenmoAccountPayload(protobuf.VenmoAccountPayload.newBuilder()
                        .setVenmoUserName(venmoUserName)
                        .setHolderName(holderName))
                .build();
    }

    public static VenmoAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        return new VenmoAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getVenmoAccountPayload().getVenmoUserName(),
                proto.getVenmoAccountPayload().getHolderName(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + Res.getWithCol("payment.account.owner") + " " + holderName + ", " +
                Res.getWithCol("payment.venmo.venmoUserName") + " " + venmoUserName;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return getPaymentDetails();
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        return super.getAgeWitnessInputData(venmoUserName.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getOwnerId() {
        return holderName;
    }
}
