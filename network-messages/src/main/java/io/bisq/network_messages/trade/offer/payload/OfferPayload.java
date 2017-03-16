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

package io.bisq.network_messages.trade.offer.payload;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.bisq.app.DevEnv;
import io.bisq.app.Version;
import io.bisq.common.util.JsonExclude;
import io.bisq.common.util.Utilities;
import io.bisq.common.wire.proto.Messages;
import io.bisq.network_messages.NodeAddress;
import io.bisq.network_messages.btc.Restrictions;
import io.bisq.network_messages.crypto.PubKeyRing;
import io.bisq.network_messages.payload.RequiresOwnerIsOnlinePayload;
import io.bisq.network_messages.payload.StoragePayload;
import io.bisq.network_messages.payment.PaymentMethod;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@ToString
@EqualsAndHashCode
public final class OfferPayload implements StoragePayload, RequiresOwnerIsOnlinePayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // That object is sent over the wire, so we need to take care of version compatibility.
    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @JsonExclude
    private static final Logger log = LoggerFactory.getLogger(OfferPayload.class);
    public static final long TTL = TimeUnit.MINUTES.toMillis(DevEnv.STRESS_TEST_MODE ? 6 : 6);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Direction {BUY, SELL}

    public enum State {
        UNDEFINED,
        OFFER_FEE_PAID,
        AVAILABLE,
        NOT_AVAILABLE,
        REMOVED,
        OFFERER_OFFLINE
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Fields for filtering offers
    @Getter
    private final Direction direction;
    @Getter
    private final String currencyCode;
    // payment method
    private final String paymentMethodName;
    @Nullable
    @Getter
    private final String countryCode;
    @Nullable
    @Getter
    private final List<String> acceptedCountryCodes;

    @Nullable
    @Getter
    private final String bankId;
    @Nullable
    @Getter
    private final List<String> acceptedBankIds;

    @Getter
    private final List<NodeAddress> arbitratorNodeAddresses;

    @Getter
    private final String id;
    @Getter
    private final long date;
    @Getter
    private final long protocolVersion;

    // We use 2 type of prices: fixed price or price based on distance from market price
    @Getter
    private final boolean useMarketBasedPrice;
    // fiatPrice if fixed price is used (usePercentageBasedPrice = false), otherwise 0

    //TODO add support for altcoin price or fix precision issue
    @Getter
    private final long fiatPrice;

    // Distance form market price if percentage based price is used (usePercentageBasedPrice = true), otherwise 0.
    // E.g. 0.1 -> 10%. Can be negative as well. Depending on direction the marketPriceMargin is above or below the market price.
    // Positive values is always the usual case where you want a better price as the market.
    // E.g. Buy offer with market price 400.- leads to a 360.- price.
    // Sell offer with market price 400.- leads to a 440.- price.
    @Getter
    private final double marketPriceMargin;
    private final long amount;
    private final long minAmount;
    @Getter
    private final NodeAddress offererNodeAddress;
    @JsonExclude
    @Getter
    private final PubKeyRing pubKeyRing;
    @Getter
    private final String offererPaymentAccountId;

    // Mutable property. Has to be set before offer is save in P2P network as it changes the objects hash!
    @Getter
    @Setter
    private String offerFeePaymentTxID;

    // New properties from v. 0.5.0.0
    @Getter
    private final String versionNr;
    private final long blockHeightAtOfferCreation;
    private final long txFee;
    private final long createOfferFee;
    private final long buyerSecurityDeposit;
    private final long sellerSecurityDeposit;
    private final long maxTradeLimit;
    @Getter
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

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility.
    @Nullable
    private Map<String, String> extraDataMap;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * no nulls are allowed because protobuffer replaces them with "" on the other side,
     * meaning it's null here and "" there => not good
     *
     * @param id
     * @param creationDate               date of OfferPayload creation, can be null in which case the current date/time will be used.
     * @param offererNodeAddress
     * @param pubKeyRing
     * @param direction
     * @param fiatPrice
     * @param marketPriceMargin
     * @param useMarketBasedPrice
     * @param amount
     * @param minAmount
     * @param currencyCode
     * @param arbitratorNodeAddresses
     * @param paymentMethodName
     * @param offererPaymentAccountId
     * @param offerFeePaymentTxID
     * @param countryCode
     * @param acceptedCountryCodes
     * @param bankId
     * @param acceptedBankIds
     * @param versionNr
     * @param blockHeightAtOfferCreation
     * @param txFee
     * @param createOfferFee
     * @param buyerSecurityDeposit
     * @param sellerSecurityDeposit
     * @param maxTradeLimit
     * @param maxTradePeriod
     * @param useAutoClose
     * @param useReOpenAfterAutoClose
     * @param lowerClosePrice
     * @param upperClosePrice
     * @param isPrivateOffer
     * @param hashOfChallenge
     * @param extraDataMap
     */
    public OfferPayload(String id,
                 Long creationDate,
                 NodeAddress offererNodeAddress,
                 PubKeyRing pubKeyRing,
                 Direction direction,
                 long fiatPrice,
                 double marketPriceMargin,
                 boolean useMarketBasedPrice,
                 long amount,
                 long minAmount,
                 String currencyCode,
                 List<NodeAddress> arbitratorNodeAddresses,
                 String paymentMethodName,
                 String offererPaymentAccountId,
                 @Nullable String offerFeePaymentTxID,
                 @Nullable String countryCode,
                 @Nullable List<String> acceptedCountryCodes,
                 @Nullable String bankId,
                 @Nullable List<String> acceptedBankIds,
                 String versionNr,
                 long blockHeightAtOfferCreation,
                 long txFee,
                 long createOfferFee,
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
        this.offererNodeAddress = offererNodeAddress;
        this.pubKeyRing = pubKeyRing;
        this.direction = direction;
        this.fiatPrice = fiatPrice;
        this.marketPriceMargin = marketPriceMargin;
        this.useMarketBasedPrice = useMarketBasedPrice;
        this.amount = amount;
        this.minAmount = minAmount;
        this.currencyCode = currencyCode;
        this.arbitratorNodeAddresses = arbitratorNodeAddresses;
        this.paymentMethodName = paymentMethodName;
        this.offererPaymentAccountId = offererPaymentAccountId;
        this.offerFeePaymentTxID = Optional.ofNullable(offerFeePaymentTxID).orElse("");
        this.countryCode = Optional.ofNullable(countryCode).orElse("");
        this.acceptedCountryCodes = Optional.ofNullable(acceptedCountryCodes).orElse(Lists.newArrayList());
        this.bankId = Optional.ofNullable(bankId).orElse("");
        this.acceptedBankIds = Optional.ofNullable(acceptedBankIds).orElse(Lists.newArrayList());
        this.versionNr = versionNr;
        this.blockHeightAtOfferCreation = blockHeightAtOfferCreation;
        this.txFee = txFee;
        this.createOfferFee = createOfferFee;
        this.buyerSecurityDeposit = buyerSecurityDeposit;
        this.sellerSecurityDeposit = sellerSecurityDeposit;
        this.maxTradeLimit = maxTradeLimit;
        this.maxTradePeriod = maxTradePeriod;
        this.useAutoClose = useAutoClose;
        this.useReOpenAfterAutoClose = useReOpenAfterAutoClose;
        this.lowerClosePrice = lowerClosePrice;
        this.upperClosePrice = upperClosePrice;
        this.isPrivateOffer = isPrivateOffer;
        this.hashOfChallenge = Optional.ofNullable(hashOfChallenge).orElse("");
        this.extraDataMap = Optional.ofNullable(extraDataMap).orElse(Maps.newHashMap());
        this.date = Optional.ofNullable(creationDate).orElse(new Date().getTime());
        this.protocolVersion = Version.TRADE_PROTOCOL_VERSION;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }


    @Override
    public NodeAddress getOwnerNodeAddress() {
        return offererNodeAddress;
    }

    //TODO update with new properties
    public void checkCoinNotNullOrZero(Coin value, String name) {
        checkNotNull(value, name + " is null");
        checkArgument(value.isPositive(),
                name + " must be positive. " + name + "=" + value.toFriendlyString());
    }

    public void validate() {
        // Coins
        checkCoinNotNullOrZero(getAmount(), "Amount");
        checkCoinNotNullOrZero(getMinAmount(), "MinAmount");
        checkCoinNotNullOrZero(getCreateOfferFee(), "CreateOfferFee");
        
        /*checkArgument(getCreateOfferFee().value >= FeeService.MIN_CREATE_OFFER_FEE_IN_BTC,
                "createOfferFee must not be less than FeeService.MIN_CREATE_OFFER_FEE_IN_BTC. " +
                        "createOfferFee=" + getCreateOfferFee().toFriendlyString());
        checkArgument(getCreateOfferFee().value <= FeeService.MAX_CREATE_OFFER_FEE_IN_BTC,
                "createOfferFee must not be larger than FeeService.MAX_CREATE_OFFER_FEE_IN_BTC. " +
                        "createOfferFee=" + getCreateOfferFee().toFriendlyString());*/
        
        checkCoinNotNullOrZero(getBuyerSecurityDeposit(), "buyerSecurityDeposit");
        checkCoinNotNullOrZero(getSellerSecurityDeposit(), "sellerSecurityDeposit");
        checkArgument(getBuyerSecurityDeposit().value >= Restrictions.MIN_BUYER_SECURITY_DEPOSIT.value,
                "buyerSecurityDeposit must not be less than Restrictions.MIN_BUYER_SECURITY_DEPOSIT. " +
                        "buyerSecurityDeposit=" + getBuyerSecurityDeposit().toFriendlyString());
        checkArgument(getBuyerSecurityDeposit().value <= Restrictions.MAX_BUYER_SECURITY_DEPOSIT.value,
                "buyerSecurityDeposit must not be larger than Restrictions.MAX_BUYER_SECURITY_DEPOSIT. " +
                        "buyerSecurityDeposit=" + getBuyerSecurityDeposit().toFriendlyString());

        checkArgument(getSellerSecurityDeposit().value == Restrictions.SELLER_SECURITY_DEPOSIT.value,
                "sellerSecurityDeposit must be equal to Restrictions.SELLER_SECURITY_DEPOSIT. " +
                        "sellerSecurityDeposit=" + getSellerSecurityDeposit().toFriendlyString());
        checkCoinNotNullOrZero(getTxFee(), "txFee");
        checkCoinNotNullOrZero(getMaxTradeLimit(), "MaxTradeLimit");

        checkArgument(getMinAmount().compareTo(Restrictions.MIN_TRADE_AMOUNT) >= 0,
                "MinAmount is less then "
                        + Restrictions.MIN_TRADE_AMOUNT.toFriendlyString());
        checkArgument(getAmount().compareTo(getPaymentMethod().getMaxTradeLimit()) <= 0,
                "Amount is larger then "
                        + getPaymentMethod().getMaxTradeLimit().toFriendlyString());
        checkArgument(getAmount().compareTo(getMinAmount()) >= 0, "MinAmount is larger then Amount");


        //
       /* checkNotNull(getPrice(), "Price is null");
        checkArgument(getPrice().isPositive(),
                "Price must be positive. price=" + getPrice().toFriendlyString());*/

        checkArgument(getDate().getTime() > 0,
                "Date must not be 0. date=" + getDate().toString());

        checkNotNull(getArbitratorNodeAddresses(), "Arbitrator is null");
        checkNotNull(getCurrencyCode(), "Currency is null");
        checkNotNull(getDirection(), "Direction is null");
        checkNotNull(getId(), "Id is null");
        checkNotNull(getPubKeyRing(), "pubKeyRing is null");
        checkNotNull(getMinAmount(), "MinAmount is null");
        //  checkNotNull(getPrice(), "Price is null");
        checkNotNull(getTxFee(), "txFee is null");
        checkNotNull(getCreateOfferFee(), "CreateOfferFee is null");
        checkNotNull(getVersionNr(), "VersionNr is null");
        checkArgument(getMaxTradePeriod() > 0, "maxTradePeriod must be positive. maxTradePeriod=" + getMaxTradePeriod());
        // TODO check upper and lower bounds for fiat
        // TODO check rest of new parameters
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public Direction getMirroredDirection() {
        return direction == Direction.BUY ? Direction.SELL : Direction.BUY;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing != null ? pubKeyRing.getSignaturePubKey() : null;
    }

    public String getShortId() {
        return Utilities.getShortId(id);
    }

    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.getPaymentMethodById(paymentMethodName);
    }

    public Coin getTxFee() {
        return Coin.valueOf(txFee);
    }

    public Coin getCreateOfferFee() {
        return Coin.valueOf(createOfferFee);
    }

    public Coin getBuyerSecurityDeposit() {
        return Coin.valueOf(buyerSecurityDeposit);
    }

    public Coin getSellerSecurityDeposit() {
        return Coin.valueOf(sellerSecurityDeposit);
    }

    public Coin getMaxTradeLimit() {
        return Coin.valueOf(maxTradeLimit);
    }

    public Coin getAmount() {
        return Coin.valueOf(amount);
    }

    public Coin getMinAmount() {
        return Coin.valueOf(minAmount);
    }

    public Date getDate() {
        return new Date(date);
    }

    @Override
    public long getTTL() {
        return TTL;
    }


    @Override
    public Messages.StoragePayload toProtoBuf() {
        Messages.Offer.Builder offerBuilder = Messages.Offer.newBuilder()
                .setTTL(TTL)
                .setDirectionValue(direction.ordinal())
                .setCurrencyCode(currencyCode)
                .setPaymentMethodName(paymentMethodName)
                .addAllArbitratorNodeAddresses(arbitratorNodeAddresses.stream()
                        .map(nodeAddress -> nodeAddress.toProtoBuf()).collect(Collectors.toList()))
                .setId(id)
                .setDate(date)
                .setProtocolVersion(protocolVersion)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setFiatPrice(fiatPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setOffererNodeAddress(offererNodeAddress.toProtoBuf())
                .setPubKeyRing(pubKeyRing.toProtoBuf())
                .setOffererPaymentAccountId(offererPaymentAccountId)
                .setVersionNr(versionNr)
                .setBlockHeightAtOfferCreation(blockHeightAtOfferCreation)
                .setTxFee(txFee)
                .setCreateOfferFee(createOfferFee)
                .setBuyerSecurityDeposit(buyerSecurityDeposit)
                .setSellerSecurityDeposit(sellerSecurityDeposit)
                .setMaxTradeLimit(maxTradeLimit)
                .setMaxTradePeriod(maxTradePeriod)
                .setUseAutoClose(useAutoClose)
                .setUseReOpenAfterAutoClose(useReOpenAfterAutoClose)
                .setLowerClosePrice(lowerClosePrice)
                .setUpperClosePrice(upperClosePrice)
                .setIsPrivateOffer(isPrivateOffer);


        if (Objects.nonNull(offerFeePaymentTxID)) {
            offerBuilder.setOfferFeePaymentTxID(offerFeePaymentTxID);
        } else {
            throw new RuntimeException("OfferPayload is in invalid state: offerFeePaymentTxID is not set when adding to P2P network.");
        }
        Optional.ofNullable(countryCode).ifPresent(offerBuilder::setCountryCode);
        Optional.ofNullable(bankId).ifPresent(offerBuilder::setBankId);
        Optional.ofNullable(acceptedCountryCodes).ifPresent(offerBuilder::addAllAcceptedCountryCodes);
        Optional.ofNullable(getAcceptedBankIds()).ifPresent(offerBuilder::addAllAcceptedBankIds);
        Optional.ofNullable(hashOfChallenge).ifPresent(offerBuilder::setHashOfChallenge);
        Optional.ofNullable(extraDataMap).ifPresent(offerBuilder::putAllExtraDataMap);

        return Messages.StoragePayload.newBuilder().setOffer(offerBuilder).build();
    }
}
