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
import bisq.common.util.JsonExclude;

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
public class AmazonGiftCardAccountPayload extends PaymentAccountPayload {
    private String emailOrMobileNr;
    // For backward compatibility we need to exclude the new field for the contract json.
    // We can remove that after a while when risk that users with pre 1.5.5 version is very low.
    @JsonExclude
    private String countryCode = "";

    public AmazonGiftCardAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AmazonGiftCardAccountPayload(String paymentMethodName,
                                         String id,
                                         String emailOrMobileNr,
                                         String countryCode,
                                         long maxTradePeriod,
                                         Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.emailOrMobileNr = emailOrMobileNr;
        this.countryCode = countryCode;
    }

    @Override
    public Message toProtoMessage() {
        protobuf.AmazonGiftCardAccountPayload.Builder builder =
                protobuf.AmazonGiftCardAccountPayload.newBuilder()
                        .setCountryCode(countryCode)
                        .setEmailOrMobileNr(emailOrMobileNr);
        return getPaymentAccountPayloadBuilder()
                .setAmazonGiftCardAccountPayload(builder)
                .build();
    }

    public static PaymentAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.AmazonGiftCardAccountPayload amazonGiftCardAccountPayload = proto.getAmazonGiftCardAccountPayload();
        return new AmazonGiftCardAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                amazonGiftCardAccountPayload.getEmailOrMobileNr(),
                amazonGiftCardAccountPayload.getCountryCode(),
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
        return Res.getWithCol("payment.email.mobile") + " " + emailOrMobileNr;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        String data = "AmazonGiftCard" + emailOrMobileNr;
        return super.getAgeWitnessInputData(data.getBytes(StandardCharsets.UTF_8));
    }

    public boolean countryNotSet() {
        return countryCode.isEmpty();
    }
}
