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

import io.bisq.generated.protobuffer.PB;

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
                              @Nullable Map<String, String> excludeFromJsonDataMap) {
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
        PB.F2FAccountPayload.Builder builder = PB.F2FAccountPayload.newBuilder()
                .setContact(contact)
                .setCity(city)
                .setExtraInfo(extraInfo);
        final PB.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setF2FAccountPayload(builder);
        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload)
                .build();
    }

    public static PaymentAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        PB.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        PB.F2FAccountPayload f2fAccountPayloadPB = countryBasedPaymentAccountPayload.getF2FAccountPayload();
        return new F2FAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                f2fAccountPayloadPB.getContact(),
                f2fAccountPayloadPB.getCity(),
                f2fAccountPayloadPB.getExtraInfo(),
                proto.getMaxTradePeriod(),
                CollectionUtils.isEmpty(proto.getExcludeFromJsonDataMap()) ? null : new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return "Face to Face - Contact: " + contact + ", city: " + city + ", additional information: " + extraInfo;
    }


    @Override
    public String getPaymentDetailsForTradePopup() {
        // We don't show here more as the makers extra data are the relevant for the trade. City has to be anyway the
        // same for maker and taker.
        return "Contact details: " + contact;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // We use here the city because the address alone seems to be too weak
        return super.getAgeWitnessInputData(ArrayUtils.addAll(contact.getBytes(Charset.forName("UTF-8")),
                city.getBytes(Charset.forName("UTF-8"))));
    }
}
