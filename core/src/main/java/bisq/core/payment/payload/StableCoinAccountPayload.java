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
 * The account data of all stablecoin payment methods: the address of the account on the network
 * of the coin. The coin and its network are given by the payment method ID (see
 * {@link bisq.core.payment.stablecoin.StableCoinPaymentRail}).
 */
@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class StableCoinAccountPayload extends PaymentAccountPayload {
    private String address = "";

    public StableCoinAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private StableCoinAccountPayload(String paymentMethod,
                                     String id,
                                     String address,
                                     long maxTradePeriod,
                                     PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.address = address;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setStableCoinAccountPayload(protobuf.StableCoinAccountPayload.newBuilder()
                        .setAddress(address))
                .build();
    }

    @SuppressWarnings("deprecation")
    public static StableCoinAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        return new StableCoinAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getStableCoinAccountPayload().getAddress(),
                proto.getMaxTradePeriod(),
                new PaymentAccountPayloadExcludeFromJsonMap(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A stablecoin payment method fixes the coin and its network, and an address can be valid on several networks.
     * So the payment account data of a trade with a stablecoin payment method must be of exactly that payment
     * method, and stablecoin account data belongs to no other payment method.
     *
     * @return true if one of the two is a stablecoin one and the payment account data is not of the payment method
     */
    public static boolean isOfAnotherPaymentMethod(PaymentMethod paymentMethod,
                                                   PaymentAccountPayload paymentAccountPayload) {
        boolean isStableCoinPaymentMethod = paymentMethod.isStableCoin();
        boolean isStableCoinAccountPayload = paymentAccountPayload instanceof StableCoinAccountPayload;
        if (!isStableCoinPaymentMethod && !isStableCoinAccountPayload) {
            return false;
        }
        return !isStableCoinPaymentMethod ||
                !isStableCoinAccountPayload ||
                !paymentMethod.getId().equals(paymentAccountPayload.getPaymentMethodId());
    }

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + getPaymentDetailsForTradePopup();
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.getWithCol("payment.stablecoin.address") + " " + address;
    }

    // A transfer on a blockchain has no "reason for payment" text
    @Override
    public boolean showRefTextWarning() {
        return false;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        return super.getAgeWitnessInputData(address.getBytes(StandardCharsets.UTF_8));
    }
}
