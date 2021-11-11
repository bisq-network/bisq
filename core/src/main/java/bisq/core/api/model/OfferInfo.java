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
import bisq.core.offer.OpenOffer;
import bisq.core.util.coin.CoinUtil;

import bisq.common.Payload;

import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode
@ToString
@Getter
public class OfferInfo implements Payload {

    // The client cannot see bisq.core.Offer or its fromProto method.  We use the lighter
    // weight OfferInfo proto wrapper instead, containing just enough fields to view,
    // create and take offers.

    private final String id;
    private final String direction;
    private final long price;
    private final boolean useMarketBasedPrice;
    private final double marketPriceMargin;
    private final long amount;
    private final long minAmount;
    private final long volume;
    private final long minVolume;
    private final long txFee;
    private final long makerFee;
    private final String offerFeePaymentTxId;
    private final long buyerSecurityDeposit;
    private final long sellerSecurityDeposit;
    private final long triggerPrice;
    private final boolean isCurrencyForMakerFeeBtc;
    private final String paymentAccountId;
    private final String paymentMethodId;
    private final String paymentMethodShortName;
    // Fiat offer:  baseCurrencyCode = BTC, counterCurrencyCode = fiat ccy code.
    // Altcoin offer:  baseCurrencyCode = altcoin ccy code, counterCurrencyCode = BTC.
    private final String baseCurrencyCode;
    private final String counterCurrencyCode;
    private final long date;
    private final String state;
    private final boolean isActivated;
    private final boolean isMyOffer;
    private final boolean isMyPendingOffer;
    private final boolean isBsqSwapOffer;
    private final String ownerNodeAddress;
    private final String pubKeyRing;
    private final String versionNumber;
    private final int protocolVersion;

    public OfferInfo(Builder builder) {
        this.id = builder.id;
        this.direction = builder.direction;
        this.price = builder.price;
        this.useMarketBasedPrice = builder.useMarketBasedPrice;
        this.marketPriceMargin = builder.marketPriceMargin;
        this.amount = builder.amount;
        this.minAmount = builder.minAmount;
        this.volume = builder.volume;
        this.minVolume = builder.minVolume;
        this.txFee = builder.txFee;
        this.makerFee = builder.makerFee;
        this.offerFeePaymentTxId = builder.offerFeePaymentTxId;
        this.buyerSecurityDeposit = builder.buyerSecurityDeposit;
        this.sellerSecurityDeposit = builder.sellerSecurityDeposit;
        this.triggerPrice = builder.triggerPrice;
        this.isCurrencyForMakerFeeBtc = builder.isCurrencyForMakerFeeBtc;
        this.paymentAccountId = builder.paymentAccountId;
        this.paymentMethodId = builder.paymentMethodId;
        this.paymentMethodShortName = builder.paymentMethodShortName;
        this.baseCurrencyCode = builder.baseCurrencyCode;
        this.counterCurrencyCode = builder.counterCurrencyCode;
        this.date = builder.date;
        this.state = builder.state;
        this.isActivated = builder.isActivated;
        this.isMyOffer = builder.isMyOffer;
        this.isMyPendingOffer = builder.isMyPendingOffer;
        this.isBsqSwapOffer = builder.isBsqSwapOffer;
        this.ownerNodeAddress = builder.ownerNodeAddress;
        this.pubKeyRing = builder.pubKeyRing;
        this.versionNumber = builder.versionNumber;
        this.protocolVersion = builder.protocolVersion;
    }

    public static OfferInfo toMyOfferInfo(Offer offer) {
        return getBuilder(offer, true).build();
    }

    public static OfferInfo toOfferInfo(Offer offer) {
        // Assume the offer is not mine, but isMyOffer can be reset to true, i.e., when
        // calling TradeInfo toTradeInfo(Trade trade, String role, boolean isMyOffer);
        return getBuilder(offer, false).build();
    }

    public static OfferInfo toMyPendingOfferInfo(Offer myNewOffer) {
        // Use this to build an OfferInfo instance when a new OpenOffer is being
        // prepared, and no valid OpenOffer state (AVAILABLE, DEACTIVATED) exists.
        // It is needed for the CLI's 'createoffer' output, which has a boolean 'ENABLED'
        // column that will show a PENDING value when this.isMyPendingOffer = true.
        return getBuilder(myNewOffer, true)
                .withIsMyPendingOffer(true)
                .build();
    }

    public static OfferInfo toMyOfferInfo(OpenOffer openOffer) {
        // An OpenOffer is always my offer.
        return getBuilder(openOffer.getOffer(), true)
                .withTriggerPrice(openOffer.getTriggerPrice())
                .withIsActivated(!openOffer.isDeactivated())
                .build();
    }

    private static Builder getBuilder(Offer offer, boolean isMyOffer) {
        return new Builder()
                .withId(offer.getId())
                .withDirection(offer.getDirection().name())
                .withPrice(Objects.requireNonNull(offer.getPrice()).getValue())
                .withUseMarketBasedPrice(offer.isUseMarketBasedPrice())
                .withMarketPriceMargin(offer.getMarketPriceMargin())
                .withAmount(offer.getAmount().value)
                .withMinAmount(offer.getMinAmount().value)
                .withVolume(Objects.requireNonNull(offer.getVolume()).getValue())
                .withMinVolume(Objects.requireNonNull(offer.getMinVolume()).getValue())
                .withMakerFee(getMakerFee(offer, isMyOffer))
                .withTxFee(offer.getTxFee().value)
                .withOfferFeePaymentTxId(offer.getOfferFeePaymentTxId())
                .withBuyerSecurityDeposit(offer.getBuyerSecurityDeposit().value)
                .withSellerSecurityDeposit(offer.getSellerSecurityDeposit().value)
                .withIsCurrencyForMakerFeeBtc(offer.isCurrencyForMakerFeeBtc())
                .withPaymentAccountId(offer.getMakerPaymentAccountId())
                .withPaymentMethodId(offer.getPaymentMethod().getId())
                .withPaymentMethodShortName(offer.getPaymentMethod().getShortName())
                .withBaseCurrencyCode(offer.getBaseCurrencyCode())
                .withCounterCurrencyCode(offer.getCounterCurrencyCode())
                .withDate(offer.getDate().getTime())
                .withState(offer.getState().name())
                .withIsMyOffer(isMyOffer)
                .withIsBsqSwapOffer(offer.isBsqSwapOffer())
                .withOwnerNodeAddress(offer.getOfferPayloadBase().getOwnerNodeAddress().getFullAddress())
                .withPubKeyRing(offer.getOfferPayloadBase().getPubKeyRing().toString())
                .withVersionNumber(offer.getOfferPayloadBase().getVersionNr())
                .withProtocolVersion(offer.getOfferPayloadBase().getProtocolVersion());
    }

    private static long getMakerFee(Offer offer, boolean isMyOffer) {
        return isMyOffer
                ? requireNonNull(CoinUtil.getMakerFee(false, offer.getAmount())).value
                : 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.OfferInfo toProtoMessage() {
        return bisq.proto.grpc.OfferInfo.newBuilder()
                .setId(id)
                .setDirection(direction)
                .setPrice(price)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setVolume(volume)
                .setMinVolume(minVolume)
                .setMakerFee(makerFee)
                .setTxFee(txFee)
                .setOfferFeePaymentTxId(isBsqSwapOffer ? "" : offerFeePaymentTxId)
                .setBuyerSecurityDeposit(buyerSecurityDeposit)
                .setSellerSecurityDeposit(sellerSecurityDeposit)
                .setTriggerPrice(triggerPrice)
                .setIsCurrencyForMakerFeeBtc(isCurrencyForMakerFeeBtc)
                .setPaymentAccountId(paymentAccountId)
                .setPaymentMethodId(paymentMethodId)
                .setPaymentMethodShortName(paymentMethodShortName)
                .setBaseCurrencyCode(baseCurrencyCode)
                .setCounterCurrencyCode(counterCurrencyCode)
                .setDate(date)
                .setState(state)
                .setIsActivated(isActivated)
                .setIsMyOffer(isMyOffer)
                .setIsMyPendingOffer(isMyPendingOffer)
                .setIsBsqSwapOffer(isBsqSwapOffer)
                .setOwnerNodeAddress(ownerNodeAddress)
                .setPubKeyRing(pubKeyRing)
                .setVersionNr(versionNumber)
                .setProtocolVersion(protocolVersion)
                .build();
    }

    @SuppressWarnings("unused")
    public static OfferInfo fromProto(bisq.proto.grpc.OfferInfo proto) {
        return new Builder()
                .withId(proto.getId())
                .withDirection(proto.getDirection())
                .withPrice(proto.getPrice())
                .withUseMarketBasedPrice(proto.getUseMarketBasedPrice())
                .withMarketPriceMargin(proto.getMarketPriceMargin())
                .withAmount(proto.getAmount())
                .withMinAmount(proto.getMinAmount())
                .withVolume(proto.getVolume())
                .withMinVolume(proto.getMinVolume())
                .withMakerFee(proto.getMakerFee())
                .withTxFee(proto.getTxFee())
                .withOfferFeePaymentTxId(proto.getOfferFeePaymentTxId())
                .withBuyerSecurityDeposit(proto.getBuyerSecurityDeposit())
                .withSellerSecurityDeposit(proto.getSellerSecurityDeposit())
                .withTriggerPrice(proto.getTriggerPrice())
                .withIsCurrencyForMakerFeeBtc(proto.getIsCurrencyForMakerFeeBtc())
                .withPaymentAccountId(proto.getPaymentAccountId())
                .withPaymentMethodId(proto.getPaymentMethodId())
                .withPaymentMethodShortName(proto.getPaymentMethodShortName())
                .withBaseCurrencyCode(proto.getBaseCurrencyCode())
                .withCounterCurrencyCode(proto.getCounterCurrencyCode())
                .withDate(proto.getDate())
                .withState(proto.getState())
                .withIsActivated(proto.getIsActivated())
                .withIsMyOffer(proto.getIsMyOffer())
                .withIsMyPendingOffer(proto.getIsMyPendingOffer())
                .withIsBsqSwapOffer(proto.getIsBsqSwapOffer())
                .withOwnerNodeAddress(proto.getOwnerNodeAddress())
                .withPubKeyRing(proto.getPubKeyRing())
                .withVersionNumber(proto.getVersionNr())
                .withProtocolVersion(proto.getProtocolVersion())
                .build();
    }

    /*
     * Builder helps avoid bungling use of a large OfferInfo constructor
     * argument list.  If consecutive argument values of the same type are not
     * ordered correctly, the compiler won't complain but the resulting bugs could
     * be hard to find and fix.
     */
    private static class Builder {
        private String id;
        private String direction;
        private long price;
        private boolean useMarketBasedPrice;
        private double marketPriceMargin;
        private long amount;
        private long minAmount;
        private long volume;
        private long minVolume;
        private long txFee;
        private long makerFee;
        private String offerFeePaymentTxId;
        private long buyerSecurityDeposit;
        private long sellerSecurityDeposit;
        private long triggerPrice;
        private boolean isCurrencyForMakerFeeBtc;
        private String paymentAccountId;
        private String paymentMethodId;
        private String paymentMethodShortName;
        private String baseCurrencyCode;
        private String counterCurrencyCode;
        private long date;
        private String state;
        private boolean isActivated;
        private boolean isMyOffer;
        private boolean isMyPendingOffer;
        private boolean isBsqSwapOffer;
        private String ownerNodeAddress;
        private String pubKeyRing;
        private String versionNumber;
        private int protocolVersion;

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withDirection(String direction) {
            this.direction = direction;
            return this;
        }

        public Builder withPrice(long price) {
            this.price = price;
            return this;
        }

        public Builder withUseMarketBasedPrice(boolean useMarketBasedPrice) {
            this.useMarketBasedPrice = useMarketBasedPrice;
            return this;
        }

        public Builder withMarketPriceMargin(double useMarketBasedPrice) {
            this.marketPriceMargin = useMarketBasedPrice;
            return this;
        }

        public Builder withAmount(long amount) {
            this.amount = amount;
            return this;
        }

        public Builder withMinAmount(long minAmount) {
            this.minAmount = minAmount;
            return this;
        }

        public Builder withVolume(long volume) {
            this.volume = volume;
            return this;
        }

        public Builder withMinVolume(long minVolume) {
            this.minVolume = minVolume;
            return this;
        }

        public Builder withTxFee(long txFee) {
            this.txFee = txFee;
            return this;
        }

        public Builder withMakerFee(long makerFee) {
            this.makerFee = makerFee;
            return this;
        }

        public Builder withOfferFeePaymentTxId(String offerFeePaymentTxId) {
            this.offerFeePaymentTxId = offerFeePaymentTxId;
            return this;
        }

        public Builder withBuyerSecurityDeposit(long buyerSecurityDeposit) {
            this.buyerSecurityDeposit = buyerSecurityDeposit;
            return this;
        }

        public Builder withSellerSecurityDeposit(long sellerSecurityDeposit) {
            this.sellerSecurityDeposit = sellerSecurityDeposit;
            return this;
        }

        public Builder withTriggerPrice(long triggerPrice) {
            this.triggerPrice = triggerPrice;
            return this;
        }

        public Builder withIsCurrencyForMakerFeeBtc(boolean isCurrencyForMakerFeeBtc) {
            this.isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc;
            return this;
        }

        public Builder withPaymentAccountId(String paymentAccountId) {
            this.paymentAccountId = paymentAccountId;
            return this;
        }

        public Builder withPaymentMethodId(String paymentMethodId) {
            this.paymentMethodId = paymentMethodId;
            return this;
        }

        public Builder withPaymentMethodShortName(String paymentMethodShortName) {
            this.paymentMethodShortName = paymentMethodShortName;
            return this;
        }

        public Builder withBaseCurrencyCode(String baseCurrencyCode) {
            this.baseCurrencyCode = baseCurrencyCode;
            return this;
        }

        public Builder withCounterCurrencyCode(String counterCurrencyCode) {
            this.counterCurrencyCode = counterCurrencyCode;
            return this;
        }

        public Builder withDate(long date) {
            this.date = date;
            return this;
        }

        public Builder withState(String state) {
            this.state = state;
            return this;
        }

        public Builder withIsActivated(boolean isActivated) {
            this.isActivated = isActivated;
            return this;
        }

        public Builder withIsMyOffer(boolean isMyOffer) {
            this.isMyOffer = isMyOffer;
            return this;
        }

        public Builder withIsMyPendingOffer(boolean isMyPendingOffer) {
            this.isMyPendingOffer = isMyPendingOffer;
            return this;
        }

        public Builder withIsBsqSwapOffer(boolean isBsqSwapOffer) {
            this.isBsqSwapOffer = isBsqSwapOffer;
            return this;
        }

        public Builder withOwnerNodeAddress(String ownerNodeAddress) {
            this.ownerNodeAddress = ownerNodeAddress;
            return this;
        }

        public Builder withPubKeyRing(String pubKeyRing) {
            this.pubKeyRing = pubKeyRing;
            return this;
        }

        public Builder withVersionNumber(String versionNumber) {
            this.versionNumber = versionNumber;
            return this;
        }

        public Builder withProtocolVersion(int protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public OfferInfo build() {
            return new OfferInfo(this);
        }
    }
}
