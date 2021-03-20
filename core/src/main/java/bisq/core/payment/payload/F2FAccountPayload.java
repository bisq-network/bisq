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
public final class F2FAccountPayload extends CountryBasedPaymentAccountPayload {
    private String contact = "";
    private String city = "";
    private String extraInfo = "";

    public F2FAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private F2FAccountPayload(String paymentMethodName,
                              String id,
                              String countryCode,
                              String contact,
                              String city,
                              String extraInfo,
                              long maxTradePeriod,
                              Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                countryCode,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.contact = contact;
        this.city = city;
        this.extraInfo = extraInfo;
    }

    @Override
    public Message toProtoMessage() {
        protobuf.F2FAccountPayload.Builder builder = protobuf.F2FAccountPayload.newBuilder()
                .setContact(contact)
                .setCity(city)
                .setExtraInfo(extraInfo);
        final protobuf.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setF2FAccountPayload(builder);
        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload)
                .build();
    }

    public static PaymentAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        protobuf.F2FAccountPayload f2fAccountPayloadPB = countryBasedPaymentAccountPayload.getF2FAccountPayload();
        return new F2FAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                f2fAccountPayloadPB.getContact(),
                f2fAccountPayloadPB.getCity(),
                f2fAccountPayloadPB.getExtraInfo(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + Res.getWithCol("payment.f2f.contact") + " " + contact + ", " +
                Res.getWithCol("payment.f2f.city") + " " + city +
                ", " + Res.getWithCol("payment.shared.extraInfo") + " " + extraInfo;
    }


    @Override
    public String getPaymentDetailsForTradePopup() {
        // We don't show here more as the makers extra data are the relevant for the trade. City has to be anyway the
        // same for maker and taker.
        return Res.getWithCol("payment.f2f.contact") + " " + contact;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // We use here the city because the address alone seems to be too weak
        return super.getAgeWitnessInputData(ArrayUtils.addAll(contact.getBytes(StandardCharsets.UTF_8),
                city.getBytes(StandardCharsets.UTF_8)));
    }
}
