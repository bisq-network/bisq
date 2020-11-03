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

import bisq.core.locale.BankUtil;
import bisq.core.locale.CountryUtil;
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
public class WesternUnionAccountPayload extends CountryBasedPaymentAccountPayload implements PayloadWithHolderName {
    private String holderName;
    private String city;
    private String state = ""; // is optional. we don't use @Nullable because it would makes UI code more complex.
    private String email;

    public WesternUnionAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private WesternUnionAccountPayload(String paymentMethodName,
                                       String id,
                                       String countryCode,
                                       String holderName,
                                       String city,
                                       String state,
                                       String email,
                                       long maxTradePeriod,
                                       Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                countryCode,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.holderName = holderName;
        this.city = city;
        this.state = state;
        this.email = email;
    }

    @Override
    public Message toProtoMessage() {
        protobuf.WesternUnionAccountPayload.Builder builder =
                protobuf.WesternUnionAccountPayload.newBuilder()
                        .setHolderName(holderName)
                        .setCity(city)
                        .setState(state)
                        .setEmail(email);

        final protobuf.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setWesternUnionAccountPayload(builder);
        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload)
                .build();
    }

    public static PaymentAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        protobuf.WesternUnionAccountPayload westernUnionAccountPayload = countryBasedPaymentAccountPayload.getWesternUnionAccountPayload();
        return new WesternUnionAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                westernUnionAccountPayload.getHolderName(),
                westernUnionAccountPayload.getCity(),
                westernUnionAccountPayload.getState(),
                westernUnionAccountPayload.getEmail(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        String cityState = BankUtil.isStateRequired(countryCode)
                ? (Res.get("payment.account.city") + " / " + Res.getWithCol("payment.account.state") + " " + city + " / " + state + "\n")
                : (Res.getWithCol("payment.account.city") + " " + city + "\n");
        return Res.getWithCol("payment.account.fullName") + " " + holderName + "\n" +
                cityState +
                Res.getWithCol("payment.country") + " " + CountryUtil.getNameByCode(countryCode) + "\n" +
                Res.getWithCol("payment.email") + " " + email;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        String all = this.countryCode +
                this.holderName +
                this.email;
        return super.getAgeWitnessInputData(all.getBytes(StandardCharsets.UTF_8));
    }
}
