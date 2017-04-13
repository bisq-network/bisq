/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.offer;

import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.util.JsonExclude;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.storage.payload.RequiresOwnerIsOnlinePayload;
import io.bisq.network.p2p.storage.payload.StoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ToString
@EqualsAndHashCode
@Getter
@Slf4j
public final class OfferPayload implements StoragePayload, RequiresOwnerIsOnlinePayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO remove once PB work is completed
    // That object is sent over the wire, so we need to take care of version compatibility.
    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public static final long TTL = TimeUnit.MINUTES.toMillis(DevEnv.STRESS_TEST_MODE ? 6 : 6);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Direction {BUY, SELL}


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final Direction direction;
    private final String baseCurrencyCode;
    private final String counterCurrencyCode;
    private final String paymentMethodId;
    @Nullable
    private final String countryCode;
    @Nullable
    private final List<String> acceptedCountryCodes;
    @Nullable
    private final String bankId;
    @Nullable
    private final List<String> acceptedBankIds;
    private final List<NodeAddress> arbitratorNodeAddresses;
    private final List<NodeAddress> mediatorNodeAddresses;
    private final String id;
    private final long date;
    private final long protocolVersion;

    // We use 2 type of prices: fixed price or price based on distance from market price
    private final boolean useMarketBasedPrice;
    // price if fixed price is used (usePercentageBasedPrice = false), otherwise 0
    private final long price;

    // Distance form market price if percentage based price is used (usePercentageBasedPrice = true), otherwise 0.
    // E.g. 0.1 -> 10%. Can be negative as well. Depending on direction the marketPriceMargin is above or below the market price.
    // Positive values is always the usual case where you want a better price as the market.
    // E.g. Buy offer with market price 400.- leads to a 360.- price.
    // Sell offer with market price 400.- leads to a 440.- price.
    private final double marketPriceMargin;
    private final long amount;
    private final long minAmount;
    private final NodeAddress makerNodeAddress;
    @JsonExclude
    private final PubKeyRing pubKeyRing;
    private final String makerPaymentAccountId;

    // Mutable property. Has to be set before offer is save in P2P network as it changes the objects hash!
    @Setter
    private String offerFeePaymentTxId;

    // New properties from v. 0.5.0.0
    private final String versionNr;
    private final long blockHeightAtOfferCreation;
    private final long txFee;
    private final long makerFee;
    private final boolean isCurrencyForMakerFeeBtc;
    private final long buyerSecurityDeposit;
    private final long sellerSecurityDeposit;
    private final long maxTradeLimit;
    private final long maxTradePeriod;

    // reserved for future use cases
    // Close offer when certain price is reached
    private final boolean useAutoClose;
    // If useReOpenAfterAutoClose=true we re-open a new offer with the remaining funds if the trade amount 
    // was less then the offer's max. trade amount.
    private final boolean useReOpenAfterAutoClose;
    // Used when useAutoClose is set for canceling the offer when lowerClosePrice is triggered
    private final long lowerClosePrice;
    // Used when useAutoClose is set for canceling the offer when upperClosePrice is triggered
    private final long upperClosePrice;
    // Reserved for possible future use to support private trades where the taker need to have an accessKey
    private final boolean isPrivateOffer;
    @Nullable
    private final String hashOfChallenge;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferPayload(String id,
                        long date,
                        NodeAddress makerNodeAddress,
                        PubKeyRing pubKeyRing,
                        Direction direction,
                        long price,
                        double marketPriceMargin,
                        boolean useMarketBasedPrice,
                        long amount,
                        long minAmount,
                        String baseCurrencyCode,
                        String counterCurrencyCode,
                        List<NodeAddress> arbitratorNodeAddresses,
                        List<NodeAddress> mediatorNodeAddresses,
                        String paymentMethodId,
                        String makerPaymentAccountId,
                        @Nullable String offerFeePaymentTxId,
                        @Nullable String countryCode,
                        @Nullable List<String> acceptedCountryCodes,
                        @Nullable String bankId,
                        @Nullable List<String> acceptedBankIds,
                        String versionNr,
                        long blockHeightAtOfferCreation,
                        long txFee,
                        long makerFee,
                        boolean isCurrencyForMakerFeeBtc,
                        long buyerSecurityDeposit,
                        long sellerSecurityDeposit,
                        long maxTradeLimit,
                        long maxTradePeriod,
                        boolean useAutoClose,
                        boolean useReOpenAfterAutoClose,
                        long lowerClosePrice,
                        long upperClosePrice,
                        boolean isPrivateOffer,
                        @Nullable String hashOfChallenge,
                        @Nullable Map<String, String> extraDataMap) {

        this.id = id;
        this.date = date;
        this.makerNodeAddress = makerNodeAddress;
        this.pubKeyRing = pubKeyRing;
        this.direction = direction;
        this.price = price;
        this.marketPriceMargin = marketPriceMargin;
        this.useMarketBasedPrice = useMarketBasedPrice;
        this.amount = amount;
        this.minAmount = minAmount;
        this.baseCurrencyCode = baseCurrencyCode;
        this.counterCurrencyCode = counterCurrencyCode;
        this.arbitratorNodeAddresses = arbitratorNodeAddresses;
        this.mediatorNodeAddresses = mediatorNodeAddresses;
        this.paymentMethodId = paymentMethodId;
        this.makerPaymentAccountId = makerPaymentAccountId;
        this.offerFeePaymentTxId = offerFeePaymentTxId;
        this.countryCode = countryCode;
        this.acceptedCountryCodes = acceptedCountryCodes;
        this.bankId = bankId;
        this.acceptedBankIds = acceptedBankIds;
        this.versionNr = versionNr;
        this.blockHeightAtOfferCreation = blockHeightAtOfferCreation;
        this.txFee = txFee;
        this.makerFee = makerFee;
        this.isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc;
        this.buyerSecurityDeposit = buyerSecurityDeposit;
        this.sellerSecurityDeposit = sellerSecurityDeposit;
        this.maxTradeLimit = maxTradeLimit;
        this.maxTradePeriod = maxTradePeriod;
        this.useAutoClose = useAutoClose;
        this.useReOpenAfterAutoClose = useReOpenAfterAutoClose;
        this.lowerClosePrice = lowerClosePrice;
        this.upperClosePrice = upperClosePrice;
        this.isPrivateOffer = isPrivateOffer;
        this.hashOfChallenge = hashOfChallenge;
        this.extraDataMap = extraDataMap;

        this.protocolVersion = Version.TRADE_PROTOCOL_VERSION;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Overridden Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public NodeAddress getOwnerNodeAddress() {
        return makerNodeAddress;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PB.StoragePayload toProto() {
        List<PB.NodeAddress> arbitratorNodeAddresses = this.arbitratorNodeAddresses.stream()
                .map(NodeAddress::toProto)
                .collect(Collectors.toList());
        List<PB.NodeAddress> mediatorNodeAddresses = this.mediatorNodeAddresses.stream()
                .map(NodeAddress::toProto)
                .collect(Collectors.toList());
        PB.OfferPayload.Builder offerBuilder = PB.OfferPayload.newBuilder()
                .setDirection(PB.OfferPayload.Direction.valueOf(direction.name()))
                .setBaseCurrencyCode(baseCurrencyCode)
                .setCounterCurrencyCode(counterCurrencyCode)
                .setPaymentMethodId(paymentMethodId)
                .addAllArbitratorNodeAddresses(arbitratorNodeAddresses)
                .addAllMediatorNodeAddresses(mediatorNodeAddresses)
                .setId(id)
                .setDate(date)
                .setProtocolVersion(protocolVersion)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setPrice(price)
                .setMarketPriceMargin(marketPriceMargin)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setMakerNodeAddress(makerNodeAddress.toProto())
                .setPubKeyRing(pubKeyRing.toProto())
                .setMakerPaymentAccountId(makerPaymentAccountId)
                .setVersionNr(versionNr)
                .setBlockHeightAtOfferCreation(blockHeightAtOfferCreation)
                .setTxFee(txFee)
                .setMakerFee(makerFee)
                .setIsCurrencyForMakerFeeBtc(isCurrencyForMakerFeeBtc)
                .setBuyerSecurityDeposit(buyerSecurityDeposit)
                .setSellerSecurityDeposit(sellerSecurityDeposit)
                .setMaxTradeLimit(maxTradeLimit)
                .setMaxTradePeriod(maxTradePeriod)
                .setUseAutoClose(useAutoClose)
                .setUseReOpenAfterAutoClose(useReOpenAfterAutoClose)
                .setLowerClosePrice(lowerClosePrice)
                .setUpperClosePrice(upperClosePrice)
                .setIsPrivateOffer(isPrivateOffer);

        if (Objects.nonNull(offerFeePaymentTxId)) {
            offerBuilder.setOfferFeePaymentTxId(offerFeePaymentTxId);
        } else {
            throw new RuntimeException("OfferPayload is in invalid state: offerFeePaymentTxID is not set when adding to P2P network.");
        }

        Optional.ofNullable(countryCode).ifPresent(offerBuilder::setCountryCode);
        Optional.ofNullable(bankId).ifPresent(offerBuilder::setBankId);
        Optional.ofNullable(acceptedBankIds).ifPresent(offerBuilder::addAllAcceptedBankIds);
        Optional.ofNullable(hashOfChallenge).ifPresent(offerBuilder::setHashOfChallenge);
        Optional.ofNullable(acceptedCountryCodes).ifPresent(offerBuilder::addAllAcceptedCountryCodes);
        Optional.ofNullable(extraDataMap).ifPresent(offerBuilder::putAllExtraDataMap);

        return PB.StoragePayload.newBuilder().setOfferPayload(offerBuilder).build();
    }

    //TODO remove
    public String getCurrencyCode() {
        if (CurrencyUtil.isCryptoCurrency(getBaseCurrencyCode()))
            return getBaseCurrencyCode();
        else
            return getCounterCurrencyCode();
    }
}
