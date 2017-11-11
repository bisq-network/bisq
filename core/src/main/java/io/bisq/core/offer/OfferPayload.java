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

package io.bisq.core.offer;

import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.util.JsonExclude;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import io.bisq.network.p2p.storage.payload.RequiresOwnerIsOnlinePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@ToString
@EqualsAndHashCode
@Getter
@Slf4j
public final class OfferPayload implements ProtectedStoragePayload, RequiresOwnerIsOnlinePayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Direction {
        BUY,
        SELL;

        public static OfferPayload.Direction fromProto(PB.OfferPayload.Direction direction) {
            return ProtoUtil.enumFromProto(OfferPayload.Direction.class, direction.name());
        }

        public static PB.OfferPayload.Direction toProtoMessage(Direction direction) {
            return PB.OfferPayload.Direction.valueOf(direction.name());
        }
    }

    // Keys for extra map
    public static final String ACCOUNT_AGE_WITNESS_HASH = "accountAgeWitnessHash";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final String id;
    private final long date;
    private final NodeAddress ownerNodeAddress;
    @JsonExclude
    private final PubKeyRing pubKeyRing;
    private final Direction direction;
    // price if fixed price is used (usePercentageBasedPrice = false), otherwise 0
    private final long price;
    // Distance form market price if percentage based price is used (usePercentageBasedPrice = true), otherwise 0.
    // E.g. 0.1 -> 10%. Can be negative as well. Depending on direction the marketPriceMargin is above or below the market price.
    // Positive values is always the usual case where you want a better price as the market.
    // E.g. Buy offer with market price 400.- leads to a 360.- price.
    // Sell offer with market price 400.- leads to a 440.- price.
    private final double marketPriceMargin;
    // We use 2 type of prices: fixed price or price based on distance from market price
    private final boolean useMarketBasedPrice;
    private final long amount;
    private final long minAmount;
    private final String baseCurrencyCode;
    private final String counterCurrencyCode;
    private final List<NodeAddress> arbitratorNodeAddresses;
    private final List<NodeAddress> mediatorNodeAddresses;
    private final String paymentMethodId;
    private final String makerPaymentAccountId;
    // Mutable property. Has to be set before offer is save in P2P network as it changes the objects hash!
    @Nullable
    @Setter
    private String offerFeePaymentTxId;
    @Nullable
    private final String countryCode;
    @Nullable
    private final List<String> acceptedCountryCodes;
    @Nullable
    private final String bankId;
    @Nullable
    private final List<String> acceptedBankIds;
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

    // extraDataMap used from v0.6 on for hashOfPaymentAccount
    // key ACCOUNT_AGE_WITNESS, value: hex string of hashOfPaymentAccount byte array
    @Nullable
    private final Map<String, String> extraDataMap;
    private final int protocolVersion;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferPayload(String id,
                        long date,
                        NodeAddress ownerNodeAddress,
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
                        @Nullable Map<String, String> extraDataMap,
                        int protocolVersion) {
        this.id = id;
        this.date = date;
        this.ownerNodeAddress = ownerNodeAddress;
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
        this.protocolVersion = protocolVersion;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.StoragePayload toProtoMessage() {
        PB.OfferPayload.Builder builder = PB.OfferPayload.newBuilder()
                .setId(id)
                .setDate(date)
                .setOwnerNodeAddress(ownerNodeAddress.toProtoMessage())
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setDirection(Direction.toProtoMessage(direction))
                .setPrice(price)
                .setMarketPriceMargin(marketPriceMargin)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setBaseCurrencyCode(baseCurrencyCode)
                .setCounterCurrencyCode(counterCurrencyCode)
                .addAllArbitratorNodeAddresses(arbitratorNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage)
                        .collect(Collectors.toList()))
                .addAllMediatorNodeAddresses(mediatorNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage)
                        .collect(Collectors.toList()))
                .setPaymentMethodId(paymentMethodId)
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
                .setIsPrivateOffer(isPrivateOffer)
                .setProtocolVersion(protocolVersion);

        builder.setOfferFeePaymentTxId(checkNotNull(offerFeePaymentTxId,
                "OfferPayload is in invalid state: offerFeePaymentTxID is not set when adding to P2P network."));

        Optional.ofNullable(countryCode).ifPresent(builder::setCountryCode);
        Optional.ofNullable(bankId).ifPresent(builder::setBankId);
        Optional.ofNullable(acceptedBankIds).ifPresent(builder::addAllAcceptedBankIds);
        Optional.ofNullable(acceptedCountryCodes).ifPresent(builder::addAllAcceptedCountryCodes);
        Optional.ofNullable(hashOfChallenge).ifPresent(builder::setHashOfChallenge);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);

        return PB.StoragePayload.newBuilder().setOfferPayload(builder).build();
    }

    public static OfferPayload fromProto(PB.OfferPayload proto) {
        checkArgument(!proto.getOfferFeePaymentTxId().isEmpty(), "OfferFeePaymentTxId must be set in PB.OfferPayload");
        List<String> acceptedBankIds = proto.getAcceptedBankIdsList().isEmpty() ?
                null : proto.getAcceptedBankIdsList().stream().collect(Collectors.toList());
        List<String> acceptedCountryCodes = proto.getAcceptedCountryCodesList().isEmpty() ?
                null : proto.getAcceptedCountryCodesList().stream().collect(Collectors.toList());
        String hashOfChallenge = ProtoUtil.stringOrNullFromProto(proto.getHashOfChallenge());
        Map<String, String> extraDataMapMap = CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                null : proto.getExtraDataMap();

        return new OfferPayload(proto.getId(),
                proto.getDate(),
                NodeAddress.fromProto(proto.getOwnerNodeAddress()),
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                OfferPayload.Direction.fromProto(proto.getDirection()),
                proto.getPrice(),
                proto.getMarketPriceMargin(),
                proto.getUseMarketBasedPrice(),
                proto.getAmount(),
                proto.getMinAmount(),
                proto.getBaseCurrencyCode(),
                proto.getCounterCurrencyCode(),
                proto.getArbitratorNodeAddressesList().stream()
                        .map(NodeAddress::fromProto)
                        .collect(Collectors.toList()),
                proto.getMediatorNodeAddressesList().stream()
                        .map(NodeAddress::fromProto)
                        .collect(Collectors.toList()),
                proto.getPaymentMethodId(),
                proto.getMakerPaymentAccountId(),
                proto.getOfferFeePaymentTxId(),
                ProtoUtil.stringOrNullFromProto(proto.getCountryCode()),
                acceptedCountryCodes,
                ProtoUtil.stringOrNullFromProto(proto.getBankId()),
                acceptedBankIds,
                proto.getVersionNr(),
                proto.getBlockHeightAtOfferCreation(),
                proto.getTxFee(),
                proto.getMakerFee(),
                proto.getIsCurrencyForMakerFeeBtc(),
                proto.getBuyerSecurityDeposit(),
                proto.getSellerSecurityDeposit(),
                proto.getMaxTradeLimit(),
                proto.getMaxTradePeriod(),
                proto.getUseAutoClose(),
                proto.getUseReOpenAfterAutoClose(),
                proto.getLowerClosePrice(),
                proto.getUpperClosePrice(),
                proto.getIsPrivateOffer(),
                hashOfChallenge,
                extraDataMapMap,
                proto.getProtocolVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TimeUnit.MINUTES.toMillis(6);
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    // In the offer we support base and counter currency
    // Fiat offers have base currency BTC and counterCurrency Fiat
    // Altcoins have base currency Altcoin and counterCurrency BTC
    // The rest of the app does not support yet that concept of base currency and counter currencies
    // so we map here for convenience
    public String getCurrencyCode() {
        return CurrencyUtil.isCryptoCurrency(getBaseCurrencyCode()) ? getBaseCurrencyCode() : getCounterCurrencyCode();
    }
}
