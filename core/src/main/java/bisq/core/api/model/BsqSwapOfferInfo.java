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

package bisq.core.api.model;

import bisq.core.offer.Offer;

import bisq.common.Payload;

import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@Getter
public class BsqSwapOfferInfo implements Payload {
    private final String id;
    private final String direction;
    private final long amount;
    private final long minAmount;
    private final long price;
    private final String makerPaymentAccountId;
    private final String paymentMethodId;
    private final String paymentMethodShortName;
    private final String baseCurrencyCode;
    private final String counterCurrencyCode;
    private final long date;
    private final String ownerNodeAddress;
    private final String pubKeyRing; // TODO ?
    private final String versionNumber;
    private final int protocolVersion;

    public BsqSwapOfferInfo(BsqSwapOfferInfoBuilder builder) {
        this.id = builder.id;
        this.direction = builder.direction;
        this.amount = builder.amount;
        this.minAmount = builder.minAmount;
        this.price = builder.price;
        this.makerPaymentAccountId = builder.makerPaymentAccountId;
        this.paymentMethodId = builder.paymentMethodId;
        this.paymentMethodShortName = builder.paymentMethodShortName;
        this.baseCurrencyCode = builder.baseCurrencyCode;
        this.counterCurrencyCode = builder.counterCurrencyCode;
        this.date = builder.date;
        this.ownerNodeAddress = builder.ownerNodeAddress;
        this.pubKeyRing = builder.pubKeyRing;
        this.versionNumber = builder.versionNumber;
        this.protocolVersion = builder.protocolVersion;
    }

    public static BsqSwapOfferInfo toBsqSwapOfferInfo(Offer offer) {
        // TODO support triggerPrice
        return getAtomicOfferInfoBuilder(offer).build();
    }

    private static BsqSwapOfferInfoBuilder getAtomicOfferInfoBuilder(Offer offer) {
        return new BsqSwapOfferInfoBuilder()
                .withId(offer.getId())
                .withDirection(offer.getDirection().name())
                .withAmount(offer.getAmount().value)
                .withMinAmount(offer.getMinAmount().value)
                .withPrice(Objects.requireNonNull(offer.getPrice()).getValue())
                //.withMakerPaymentAccountId(offer.getOfferPayloadI().getMakerPaymentAccountId())
                //.withPaymentMethodId(offer.getOfferPayloadI().getPaymentMethodId())
                //.withPaymentMethodShortName(getPaymentMethodById(offer.getOfferPayloadI().getPaymentMethodId()).getShortName())
                .withBaseCurrencyCode(offer.getOfferPayloadBase().getBaseCurrencyCode())
                .withCounterCurrencyCode(offer.getOfferPayloadBase().getCounterCurrencyCode())
                .withDate(offer.getDate().getTime())
                .withOwnerNodeAddress(offer.getOfferPayloadBase().getOwnerNodeAddress().getFullAddress())
                .withPubKeyRing(offer.getOfferPayloadBase().getPubKeyRing().toString())
                .withVersionNumber(offer.getOfferPayloadBase().getVersionNr())
                .withProtocolVersion(offer.getOfferPayloadBase().getProtocolVersion());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.BsqSwapOfferInfo toProtoMessage() {
        return bisq.proto.grpc.BsqSwapOfferInfo.newBuilder()
                .setId(id)
                .setDirection(direction)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setPrice(price)
                .setBaseCurrencyCode(baseCurrencyCode)
                .setCounterCurrencyCode(counterCurrencyCode)
                .setDate(date)
                .setOwnerNodeAddress(ownerNodeAddress)
                .setPubKeyRing(pubKeyRing)
                .setVersionNr(versionNumber)
                .setProtocolVersion(protocolVersion)
                .build();
    }

    public static BsqSwapOfferInfo fromProto(bisq.proto.grpc.BsqSwapOfferInfo proto) {
        return new BsqSwapOfferInfoBuilder()
                .withId(proto.getId())
                .withDirection(proto.getDirection())
                .withAmount(proto.getAmount())
                .withMinAmount(proto.getMinAmount())
                .withPrice(proto.getPrice())
                .withBaseCurrencyCode(proto.getBaseCurrencyCode())
                .withCounterCurrencyCode(proto.getCounterCurrencyCode())
                .withDate(proto.getDate())
                .withOwnerNodeAddress(proto.getOwnerNodeAddress())
                .withPubKeyRing(proto.getPubKeyRing())
                .withVersionNumber(proto.getVersionNr())
                .withProtocolVersion(proto.getProtocolVersion())
                .build();
    }

    public static class BsqSwapOfferInfoBuilder {
        private String id;
        private String direction;
        private long amount;
        private long minAmount;
        private long price;
        private String makerPaymentAccountId;
        private String paymentMethodId;
        private String paymentMethodShortName;
        private String baseCurrencyCode;
        private String counterCurrencyCode;
        private long date;
        private String ownerNodeAddress;
        private String pubKeyRing;
        private String versionNumber;
        private int protocolVersion;

        public BsqSwapOfferInfoBuilder withId(String id) {
            this.id = id;
            return this;
        }

        public BsqSwapOfferInfoBuilder withDirection(String direction) {
            this.direction = direction;
            return this;
        }

        public BsqSwapOfferInfoBuilder withAmount(long amount) {
            this.amount = amount;
            return this;
        }

        public BsqSwapOfferInfoBuilder withMinAmount(long minAmount) {
            this.minAmount = minAmount;
            return this;
        }

        public BsqSwapOfferInfoBuilder withPrice(long price) {
            this.price = price;
            return this;
        }

        public BsqSwapOfferInfoBuilder withMakerPaymentAccountId(String makerPaymentAccountId) {
            this.makerPaymentAccountId = makerPaymentAccountId;
            return this;
        }

        public BsqSwapOfferInfoBuilder withPaymentMethodId(String paymentMethodId) {
            this.paymentMethodId = paymentMethodId;
            return this;
        }

        public BsqSwapOfferInfoBuilder withPaymentMethodShortName(String paymentMethodShortName) {
            this.paymentMethodShortName = paymentMethodShortName;
            return this;
        }

        public BsqSwapOfferInfoBuilder withBaseCurrencyCode(String baseCurrencyCode) {
            this.baseCurrencyCode = baseCurrencyCode;
            return this;
        }

        public BsqSwapOfferInfoBuilder withCounterCurrencyCode(String counterCurrencyCode) {
            this.counterCurrencyCode = counterCurrencyCode;
            return this;
        }

        public BsqSwapOfferInfoBuilder withDate(long date) {
            this.date = date;
            return this;
        }

        public BsqSwapOfferInfoBuilder withOwnerNodeAddress(String ownerNodeAddress) {
            this.ownerNodeAddress = ownerNodeAddress;
            return this;
        }

        public BsqSwapOfferInfoBuilder withPubKeyRing(String pubKeyRing) {
            this.pubKeyRing = pubKeyRing;
            return this;
        }

        public BsqSwapOfferInfoBuilder withVersionNumber(String versionNumber) {
            this.versionNumber = versionNumber;
            return this;
        }

        public BsqSwapOfferInfoBuilder withProtocolVersion(int protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public BsqSwapOfferInfo build() {
            return new BsqSwapOfferInfo(this);
        }
    }
}
