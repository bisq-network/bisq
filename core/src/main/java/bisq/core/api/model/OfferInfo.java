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

import bisq.core.api.model.builder.OfferInfoBuilder;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.util.coin.CoinUtil;

import bisq.common.Payload;

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

    public OfferInfo(OfferInfoBuilder builder) {
        this.id = builder.getId();
        this.direction = builder.getDirection();
        this.price = builder.getPrice();
        this.useMarketBasedPrice = builder.isUseMarketBasedPrice();
        this.marketPriceMargin = builder.getMarketPriceMargin();
        this.amount = builder.getAmount();
        this.minAmount = builder.getMinAmount();
        this.volume = builder.getVolume();
        this.minVolume = builder.getMinVolume();
        this.txFee = builder.getTxFee();
        this.makerFee = builder.getMakerFee();
        this.offerFeePaymentTxId = builder.getOfferFeePaymentTxId();
        this.buyerSecurityDeposit = builder.getBuyerSecurityDeposit();
        this.sellerSecurityDeposit = builder.getSellerSecurityDeposit();
        this.triggerPrice = builder.getTriggerPrice();
        this.isCurrencyForMakerFeeBtc = builder.isCurrencyForMakerFeeBtc();
        this.paymentAccountId = builder.getPaymentAccountId();
        this.paymentMethodId = builder.getPaymentMethodId();
        this.paymentMethodShortName = builder.getPaymentMethodShortName();
        this.baseCurrencyCode = builder.getBaseCurrencyCode();
        this.counterCurrencyCode = builder.getCounterCurrencyCode();
        this.date = builder.getDate();
        this.state = builder.getState();
        this.isActivated = builder.isActivated();
        this.isMyOffer = builder.isMyOffer();
        this.isMyPendingOffer = builder.isMyPendingOffer();
        this.isBsqSwapOffer = builder.isBsqSwapOffer();
        this.ownerNodeAddress = builder.getOwnerNodeAddress();
        this.pubKeyRing = builder.getPubKeyRing();
        this.versionNumber = builder.getVersionNumber();
        this.protocolVersion = builder.getProtocolVersion();
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

    private static OfferInfoBuilder getBuilder(Offer offer, boolean isMyOffer) {
        return new OfferInfoBuilder()
                .withId(offer.getId())
                .withDirection(offer.getDirection().name())
                .withPrice(requireNonNull(offer.getPrice()).getValue())
                .withUseMarketBasedPrice(offer.isUseMarketBasedPrice())
                .withMarketPriceMargin(offer.getMarketPriceMargin())
                .withAmount(offer.getAmount().value)
                .withMinAmount(offer.getMinAmount().value)
                .withVolume(requireNonNull(offer.getVolume()).getValue())
                .withMinVolume(requireNonNull(offer.getMinVolume()).getValue())
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
        // TODO Find out why offer.makerFee is always set to 0 when offer is bsq-swap.
        if (isMyOffer) {
            return offer.isBsqSwapOffer()
                    ? requireNonNull(CoinUtil.getMakerFee(false, offer.getAmount())).value
                    : offer.getMakerFee().value;
        } else {
            return 0;
        }
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
        return new OfferInfoBuilder()
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
}
