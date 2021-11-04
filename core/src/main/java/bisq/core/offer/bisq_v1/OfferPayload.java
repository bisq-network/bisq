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

package bisq.core.offer.bisq_v1;

import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferPayloadBase;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Hash;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.CollectionUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import java.lang.reflect.Type;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// OfferPayload has about 1.4 kb. We should look into options to make it smaller but will be hard to do it in a
// backward compatible way. Maybe a candidate when segwit activation is done as hardfork?
@EqualsAndHashCode(callSuper = true)
@Getter
@Slf4j
public final class OfferPayload extends OfferPayloadBase {
    // Keys for extra map
    // Only set for fiat offers
    public static final String ACCOUNT_AGE_WITNESS_HASH = "accountAgeWitnessHash";
    public static final String REFERRAL_ID = "referralId";
    // Only used in payment method F2F
    public static final String F2F_CITY = "f2fCity";
    public static final String F2F_EXTRA_INFO = "f2fExtraInfo";
    public static final String CASH_BY_MAIL_EXTRA_INFO = "cashByMailExtraInfo";

    // Comma separated list of ordinal of a bisq.common.app.Capability. E.g. ordinal of
    // Capability.SIGNED_ACCOUNT_AGE_WITNESS is 11 and Capability.MEDIATION is 12 so if we want to signal that maker
    // of the offer supports both capabilities we add "11, 12" to capabilities.
    public static final String CAPABILITIES = "capabilities";
    // If maker is seller and has xmrAutoConf enabled it is set to "1" otherwise it is not set
    public static final String XMR_AUTO_CONF = "xmrAutoConf";
    public static final String XMR_AUTO_CONF_ENABLED_VALUE = "1";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Distance form market price if percentage based price is used (usePercentageBasedPrice = true), otherwise 0.
    // E.g. 0.1 -> 10%. Can be negative as well. Depending on direction the marketPriceMargin is above or below the market price.
    // Positive values is always the usual case where you want a better price as the market.
    // E.g. Buy offer with market price 400.- leads to a 360.- price.
    // Sell offer with market price 400.- leads to a 440.- price.
    private final double marketPriceMargin;
    // We use 2 type of prices: fixed price or price based on distance from market price
    private final boolean useMarketBasedPrice;

    @Deprecated
    // Not used anymore, but we cannot set it Nullable or remove it to not break backward compatibility (diff. hash)
    private final List<NodeAddress> arbitratorNodeAddresses;
    @Deprecated
    // Not used anymore, but we cannot set it Nullable or remove it to not break backward compatibility (diff. hash)
    private final List<NodeAddress> mediatorNodeAddresses;

    // Mutable property. Has to be set before offer is saved in P2P network as it changes the payload hash!
    @Setter
    @Nullable
    private String offerFeePaymentTxId;
    @Nullable
    private final String countryCode;
    @Nullable
    private final List<String> acceptedCountryCodes;
    @Nullable
    private final String bankId;
    @Nullable
    private final List<String> acceptedBankIds;
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
    // was less than the offer's max. trade amount.
    private final boolean useReOpenAfterAutoClose;
    // Used when useAutoClose is set for canceling the offer when lowerClosePrice is triggered
    private final long lowerClosePrice;
    // Used when useAutoClose is set for canceling the offer when upperClosePrice is triggered
    private final long upperClosePrice;
    // Reserved for possible future use to support private trades where the taker needs to have an accessKey
    private final boolean isPrivateOffer;
    @Nullable
    private final String hashOfChallenge;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferPayload(String id,
                        long date,
                        NodeAddress ownerNodeAddress,
                        PubKeyRing pubKeyRing,
                        OfferDirection direction,
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
        super(id,
                date,
                ownerNodeAddress,
                pubKeyRing,
                baseCurrencyCode,
                counterCurrencyCode,
                direction,
                price,
                amount,
                minAmount,
                paymentMethodId,
                makerPaymentAccountId,
                extraDataMap,
                versionNr,
                protocolVersion);

        this.marketPriceMargin = marketPriceMargin;
        this.useMarketBasedPrice = useMarketBasedPrice;
        this.arbitratorNodeAddresses = arbitratorNodeAddresses;
        this.mediatorNodeAddresses = mediatorNodeAddresses;
        this.offerFeePaymentTxId = offerFeePaymentTxId;
        this.countryCode = countryCode;
        this.acceptedCountryCodes = acceptedCountryCodes;
        this.bankId = bankId;
        this.acceptedBankIds = acceptedBankIds;
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
    }

    @Override
    public byte[] getHash() {
        if (this.hash == null && this.offerFeePaymentTxId != null) {
            // A proto message can be created only after the offerFeePaymentTxId is
            // set to a non-null value;  now is the time to cache the payload hash.
            this.hash = Hash.getSha256Hash(this.toProtoMessage().toByteArray());
        }
        return this.hash;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        protobuf.OfferPayload.Builder builder = protobuf.OfferPayload.newBuilder()
                .setId(id)
                .setDate(date)
                .setOwnerNodeAddress(ownerNodeAddress.toProtoMessage())
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setDirection(OfferDirection.toProtoMessage(direction))
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

        return protobuf.StoragePayload.newBuilder().setOfferPayload(builder).build();
    }

    public static OfferPayload fromProto(protobuf.OfferPayload proto) {
        checkArgument(!proto.getOfferFeePaymentTxId().isEmpty(), "OfferFeePaymentTxId must be set in PB.OfferPayload");
        List<String> acceptedBankIds = proto.getAcceptedBankIdsList().isEmpty() ?
                null : new ArrayList<>(proto.getAcceptedBankIdsList());
        List<String> acceptedCountryCodes = proto.getAcceptedCountryCodesList().isEmpty() ?
                null : new ArrayList<>(proto.getAcceptedCountryCodesList());
        String hashOfChallenge = ProtoUtil.stringOrNullFromProto(proto.getHashOfChallenge());
        Map<String, String> extraDataMapMap = CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                null : proto.getExtraDataMap();

        return new OfferPayload(proto.getId(),
                proto.getDate(),
                NodeAddress.fromProto(proto.getOwnerNodeAddress()),
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                OfferDirection.fromProto(proto.getDirection()),
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

    @Override
    public String toString() {
        return "OfferPayload{" +
                "\r\n     marketPriceMargin=" + marketPriceMargin +
                ",\r\n     useMarketBasedPrice=" + useMarketBasedPrice +
                ",\r\n     arbitratorNodeAddresses=" + arbitratorNodeAddresses +
                ",\r\n     mediatorNodeAddresses=" + mediatorNodeAddresses +
                ",\r\n     offerFeePaymentTxId='" + offerFeePaymentTxId + '\'' +
                ",\r\n     countryCode='" + countryCode + '\'' +
                ",\r\n     acceptedCountryCodes=" + acceptedCountryCodes +
                ",\r\n     bankId='" + bankId + '\'' +
                ",\r\n     acceptedBankIds=" + acceptedBankIds +
                ",\r\n     blockHeightAtOfferCreation=" + blockHeightAtOfferCreation +
                ",\r\n     txFee=" + txFee +
                ",\r\n     makerFee=" + makerFee +
                ",\r\n     isCurrencyForMakerFeeBtc=" + isCurrencyForMakerFeeBtc +
                ",\r\n     buyerSecurityDeposit=" + buyerSecurityDeposit +
                ",\r\n     sellerSecurityDeposit=" + sellerSecurityDeposit +
                ",\r\n     maxTradeLimit=" + maxTradeLimit +
                ",\r\n     maxTradePeriod=" + maxTradePeriod +
                ",\r\n     useAutoClose=" + useAutoClose +
                ",\r\n     useReOpenAfterAutoClose=" + useReOpenAfterAutoClose +
                ",\r\n     lowerClosePrice=" + lowerClosePrice +
                ",\r\n     upperClosePrice=" + upperClosePrice +
                ",\r\n     isPrivateOffer=" + isPrivateOffer +
                ",\r\n     hashOfChallenge='" + hashOfChallenge + '\'' +
                "\r\n} " + super.toString();
    }

    // For backward compatibility we need to ensure same order for json fields as with 1.7.5. and earlier versions.
    // The json is used for the hash in the contract and change of oder would cause a different hash and
    // therefore a failure during trade.
    public static class JsonSerializer implements com.google.gson.JsonSerializer<OfferPayload> {
        @Override
        public JsonElement serialize(OfferPayload offerPayload, Type type, JsonSerializationContext context) {
            JsonObject object = new JsonObject();
            object.add("id", context.serialize(offerPayload.getId()));
            object.add("date", context.serialize(offerPayload.getDate()));
            object.add("ownerNodeAddress", context.serialize(offerPayload.getOwnerNodeAddress()));
            object.add("direction", context.serialize(offerPayload.getDirection()));
            object.add("price", context.serialize(offerPayload.getPrice()));
            object.add("marketPriceMargin", context.serialize(offerPayload.getMarketPriceMargin()));
            object.add("useMarketBasedPrice", context.serialize(offerPayload.isUseMarketBasedPrice()));
            object.add("amount", context.serialize(offerPayload.getAmount()));
            object.add("minAmount", context.serialize(offerPayload.getMinAmount()));
            object.add("baseCurrencyCode", context.serialize(offerPayload.getBaseCurrencyCode()));
            object.add("counterCurrencyCode", context.serialize(offerPayload.getCounterCurrencyCode()));
            object.add("arbitratorNodeAddresses", context.serialize(offerPayload.getArbitratorNodeAddresses()));
            object.add("mediatorNodeAddresses", context.serialize(offerPayload.getMediatorNodeAddresses()));
            object.add("paymentMethodId", context.serialize(offerPayload.getPaymentMethodId()));
            object.add("makerPaymentAccountId", context.serialize(offerPayload.getMakerPaymentAccountId()));
            object.add("offerFeePaymentTxId", context.serialize(offerPayload.getOfferFeePaymentTxId()));
            object.add("versionNr", context.serialize(offerPayload.getVersionNr()));
            object.add("blockHeightAtOfferCreation", context.serialize(offerPayload.getBlockHeightAtOfferCreation()));
            object.add("txFee", context.serialize(offerPayload.getTxFee()));
            object.add("makerFee", context.serialize(offerPayload.getMakerFee()));
            object.add("isCurrencyForMakerFeeBtc", context.serialize(offerPayload.isCurrencyForMakerFeeBtc()));
            object.add("buyerSecurityDeposit", context.serialize(offerPayload.getBuyerSecurityDeposit()));
            object.add("sellerSecurityDeposit", context.serialize(offerPayload.getSellerSecurityDeposit()));
            object.add("maxTradeLimit", context.serialize(offerPayload.getMaxTradeLimit()));
            object.add("maxTradePeriod", context.serialize(offerPayload.getMaxTradePeriod()));
            object.add("useAutoClose", context.serialize(offerPayload.isUseAutoClose()));
            object.add("useReOpenAfterAutoClose", context.serialize(offerPayload.isUseReOpenAfterAutoClose()));
            object.add("lowerClosePrice", context.serialize(offerPayload.getLowerClosePrice()));
            object.add("upperClosePrice", context.serialize(offerPayload.getUpperClosePrice()));
            object.add("isPrivateOffer", context.serialize(offerPayload.isPrivateOffer()));
            object.add("extraDataMap", context.serialize(offerPayload.getExtraDataMap()));
            object.add("protocolVersion", context.serialize(offerPayload.getProtocolVersion()));
            return object;
        }
    }
}
