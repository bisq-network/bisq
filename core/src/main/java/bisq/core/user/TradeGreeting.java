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

package bisq.core.user;

import bisq.core.offer.OfferDirection;

import bisq.common.proto.persistable.PersistablePayload;

import com.google.protobuf.Message;

import lombok.Getter;

import org.jetbrains.annotations.Nullable;

public final class TradeGreeting implements PersistablePayload {
    @Getter
    private final String greeting;
    @Nullable
    private final OfferDirection offerDirection;
    @Getter
    private final String paymentMethodId;

    public TradeGreeting(String greeting, @Nullable OfferDirection offerDirection, String paymentMethodId) {
        this.greeting = greeting;
        this.offerDirection = offerDirection;
        this.paymentMethodId = paymentMethodId;
    }

    public String getOfferDirection() {
        return offerDirection == null ? "" : offerDirection.toString();
    }

    @Override
    public Message toProtoMessage() {
        protobuf.TradeGreeting.Builder retVal = protobuf.TradeGreeting.newBuilder()
                .setGreetingText(greeting);
        if (offerDirection != null)
            retVal.setOfferDirection(OfferDirection.toProtoMessage(offerDirection));
        if (paymentMethodId != null)
            retVal.setPaymentMethodId(paymentMethodId);
        return retVal.build();
    }

    public static TradeGreeting fromProto(protobuf.TradeGreeting proto) {
        return new TradeGreeting(proto.getGreetingText(),
                OfferDirection.fromProto(proto.getOfferDirection()),
                proto.getPaymentMethodId());
    }

    public boolean equals(TradeGreeting other) {
        return (this.greeting.equals(other.greeting) &&
                this.offerDirection == other.offerDirection &&
                this.paymentMethodId.equals(other.paymentMethodId));
    }

    public boolean equalsIgnoringGreetingText(TradeGreeting other) {
        return (this.offerDirection == other.offerDirection &&
                this.paymentMethodId.equals(other.paymentMethodId));
    }

    public String toString() {
        String optDirection = offerDirection == null ? "" : "OfferType: " + offerDirection.toString();
        String optPaymentMethod = paymentMethodId.isEmpty() ? "" : " PaymentMethod: " + paymentMethodId;
        String result = optDirection;
        if (optPaymentMethod.length() > 0)
            result += (result.isEmpty() ? optPaymentMethod : " / " + optPaymentMethod);

        result += (result.isEmpty() ? "Message: " + greeting : " / Message: " + greeting);
        return result;
    }
}
