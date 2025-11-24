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
public final class MercadoPagoAccountPayload extends CountryBasedPaymentAccountPayload {
    private String accountHolderName = "";
    private String accountHolderId = "";

    public MercadoPagoAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }

    private MercadoPagoAccountPayload(String paymentMethod,
                                         String id,
                                         String countryCode,
                                         String accountHolderName,
                                         String accountHolderId,
                                         long maxTradePeriod,
                                         Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                countryCode,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.accountHolderName = accountHolderName;
        this.accountHolderId = accountHolderId;
    }

    @Override
    public Message toProtoMessage() {
        protobuf.MercadoPagoAccountPayload.Builder builder = protobuf.MercadoPagoAccountPayload.newBuilder()
                .setHolderName(accountHolderName)
                .setHolderId(accountHolderId);
        final protobuf.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setMercadoPagoAccountPayload(builder);
        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload)
                .build();
    }

    @SuppressWarnings("deprecation")
    public static MercadoPagoAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        protobuf.MercadoPagoAccountPayload mercadoPagoAccountPayloadPB = countryBasedPaymentAccountPayload.getMercadoPagoAccountPayload();
        return new MercadoPagoAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                mercadoPagoAccountPayloadPB.getHolderName(),
                mercadoPagoAccountPayloadPB.getHolderId(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.get("payment.mercadoPago.holderId") + ": " + accountHolderId + "\n" +
               Res.get("payment.account.owner.fullname") + ": " + accountHolderName;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        String all = this.accountHolderId + this.accountHolderName;
        return super.getAgeWitnessInputData(all.getBytes(StandardCharsets.UTF_8));
    }
}
