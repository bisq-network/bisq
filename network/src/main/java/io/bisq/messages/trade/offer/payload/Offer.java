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

package io.bisq.messages.trade.offer.payload;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.bisq.app.DevEnv;
import io.bisq.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.JsonExclude;
import io.bisq.common.util.MathUtils;
import io.bisq.common.util.Utilities;
import io.bisq.common.wire.proto.Messages;
import io.bisq.locale.Res;
import io.bisq.messages.btc.Restrictions;
import io.bisq.messages.btc.provider.fee.FeeService;
import io.bisq.messages.locale.CurrencyUtil;
import io.bisq.messages.payment.PaymentMethod;
import io.bisq.messages.protocol.availability.OfferAvailabilityModel;
import io.bisq.messages.protocol.availability.OfferAvailabilityProtocol;
import io.bisq.messages.provider.price.MarketPrice;
import io.bisq.messages.provider.price.PriceFeedService;
import io.bisq.messages.trade.exceptions.MarketPriceNotAvailableException;
import io.bisq.messages.trade.exceptions.TradePriceOutOfToleranceException;
import io.bisq.p2p.NodeAddress;
import io.bisq.p2p.storage.payload.RequiresOwnerIsOnlinePayload;
import io.bisq.p2p.storage.payload.StoragePayload;
import javafx.beans.property.*;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.PublicKey;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@ToString
@EqualsAndHashCode
// TODO refactor to remove logic, should be value a object only
public final class Offer implements StoragePayload, RequiresOwnerIsOnlinePayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // That object is sent over the wire, so we need to take care of version compatibility.
    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @JsonExclude
    private static final Logger log = LoggerFactory.getLogger(Offer.class);
    public static final long TTL = TimeUnit.MINUTES.toMillis(DevEnv.STRESS_TEST_MODE ? 6 : 6);
    public final static String TAC_OFFERER = Res.get("createOffer.tac");
    public static final String TAC_TAKER = Res.get("takeOffer.tac");


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
    private final Direction direction;
    private final String currencyCode;
    // payment method
    private final String paymentMethodName;
    @Nullable
    private final String countryCode;
    @Nullable
    private final List<String> acceptedCountryCodes;

    @Nullable
    private final String bankId;
    @Nullable
    private final List<String> acceptedBankIds;

    private final List<NodeAddress> arbitratorNodeAddresses;

    private final String id;
    private final long date;
    private final long protocolVersion;

    // We use 2 type of prices: fixed price or price based on distance from market price
    private final boolean useMarketBasedPrice;
    // fiatPrice if fixed price is used (usePercentageBasedPrice = false), otherwise 0

    //TODO add support for altcoin price or fix precision issue
    private final long fiatPrice;

    // Distance form market price if percentage based price is used (usePercentageBasedPrice = true), otherwise 0.
    // E.g. 0.1 -> 10%. Can be negative as well. Depending on direction the marketPriceMargin is above or below the market price.
    // Positive values is always the usual case where you want a better price as the market.
    // E.g. Buy offer with market price 400.- leads to a 360.- price.
    // Sell offer with market price 400.- leads to a 440.- price.
    private final double marketPriceMargin;

    private final long amount;
    private final long minAmount;
    private final NodeAddress offererNodeAddress;
    @JsonExclude
    private final PubKeyRing pubKeyRing;
    private final String offererPaymentAccountId;

    // Mutable property. Has to be set before offer is save in P2P network as it changes the objects hash!
    private String offerFeePaymentTxID;

    // New properties from v. 0.5.0.0
    private final String versionNr;
    private final long blockHeightAtOfferCreation;
    private final long txFee;
    private final long createOfferFee;
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

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility.
    @Nullable
    private Map<String, String> extraDataMap;

    // TODO refactor those out of Offer, offer should be pure value object
    @JsonExclude
    transient private State state = State.UNDEFINED;
    // Those state properties are transient and only used at runtime!
    // don't access directly as it might be null; use getStateProperty() which creates an object if not instantiated
    @JsonExclude
    transient private ObjectProperty<State> stateProperty = new SimpleObjectProperty<>(state);
    @JsonExclude
    @Nullable
    transient private OfferAvailabilityProtocol availabilityProtocol;
    @JsonExclude
    transient private StringProperty errorMessageProperty = new SimpleStringProperty();
    @JsonExclude
    transient private PriceFeedService priceFeedService;
    @JsonExclude
    transient private DecimalFormat decimalFormat;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * no nulls are allowed because protobuffer replaces them with "" on the other side,
     * meaning it's null here and "" there => not good
     *
     * @param id
     * @param creationDate               date of Offer creation, can be null in which case the current date/time will be used.
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
     * @param priceFeedService
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
    public Offer(String id,
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
                 PriceFeedService priceFeedService,
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
        this.priceFeedService = priceFeedService;
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

        setState(State.UNDEFINED);
        init();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    private void init() {
        stateProperty = new SimpleObjectProperty<>(State.UNDEFINED);

        // we don't need to fill it as the error message is only relevant locally, so we don't store it in the transmitted object
        errorMessageProperty = new SimpleStringProperty();
        decimalFormat = new DecimalFormat("#.#");
        decimalFormat.setMaximumFractionDigits(Fiat.SMALLEST_UNIT_EXPONENT);
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
        checkArgument(getCreateOfferFee().value >= FeeService.MIN_CREATE_OFFER_FEE_IN_BTC,
                "createOfferFee must not be less than FeeService.MIN_CREATE_OFFER_FEE_IN_BTC. " +
                        "createOfferFee=" + getCreateOfferFee().toFriendlyString());
        checkArgument(getCreateOfferFee().value <= FeeService.MAX_CREATE_OFFER_FEE_IN_BTC,
                "createOfferFee must not be larger than FeeService.MAX_CREATE_OFFER_FEE_IN_BTC. " +
                        "createOfferFee=" + getCreateOfferFee().toFriendlyString());
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
        checkNotNull(getPrice(), "Price is null");
        checkArgument(getPrice().isPositive(),
                "Price must be positive. price=" + getPrice().toFriendlyString());

        checkArgument(getDate().getTime() > 0,
                "Date must not be 0. date=" + getDate().toString());

        checkNotNull(getArbitratorNodeAddresses(), "Arbitrator is null");
        checkNotNull(getCurrencyCode(), "Currency is null");
        checkNotNull(getDirection(), "Direction is null");
        checkNotNull(getId(), "Id is null");
        checkNotNull(getPubKeyRing(), "pubKeyRing is null");
        checkNotNull(getVersionNr(), "VersionNr is null");
        checkArgument(getMaxTradePeriod() > 0, "maxTradePeriod must be positive. maxTradePeriod=" + getMaxTradePeriod());

        // TODO check upper and lower bounds for fiat
        // TODO check rest of new parameters
    }

    public void resetState() {
        setState(State.UNDEFINED);
    }

    public boolean isMyOffer(KeyRing keyRing) {
        return getPubKeyRing().equals(keyRing.getPubKeyRing());
    }

    @Nullable
    public Fiat getVolumeByAmount(Coin amount) {
        Fiat price = getPrice();
        if (price != null && amount != null) {
            try {
                return new ExchangeRate(price).coinToFiat(amount);
            } catch (Throwable t) {
                log.error("getVolumeByAmount failed. Error=" + t.getMessage());
                return null;
            }
        } else {
            return null;
        }
    }

    @Nullable
    public Fiat getOfferVolume() {
        return getVolumeByAmount(getAmount());
    }

    @Nullable
    public Fiat getMinOfferVolume() {
        return getVolumeByAmount(getMinAmount());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Availability
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO refactor those out of Offer, offer should be pure value object
    public void checkOfferAvailability(OfferAvailabilityModel model, ResultHandler resultHandler,
                                       ErrorMessageHandler errorMessageHandler) {
        availabilityProtocol = new OfferAvailabilityProtocol(model,
                () -> {
                    cancelAvailabilityRequest();
                    resultHandler.handleResult();
                },
                (errorMessage) -> {
                    if (availabilityProtocol != null)
                        availabilityProtocol.cancel();
                    log.error(errorMessage);
                    errorMessageHandler.handleErrorMessage(errorMessage);
                });
        availabilityProtocol.sendOfferAvailabilityRequest();
    }

    // TODO refactor those out of Offer, offer should be pure value object
    public void cancelAvailabilityRequest() {
        if (availabilityProtocol != null)
            availabilityProtocol.cancel();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void setPriceFeedService(PriceFeedService priceFeedService) {
        this.priceFeedService = priceFeedService;
    }

    public void setState(State state) {
        this.state = state;
        stateProperty().set(state);
    }

    public void setOfferFeePaymentTxID(String offerFeePaymentTxID) {
        this.offerFeePaymentTxID = offerFeePaymentTxID;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessageProperty.set(errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing != null ? pubKeyRing.getSignaturePubKey() : null;
    }

    public long getProtocolVersion() {
        return protocolVersion;
    }

    public String getId() {
        return id;
    }

    public String getShortId() {
        return Utilities.getShortId(id);
    }

    public NodeAddress getOffererNodeAddress() {
        return offererNodeAddress;
    }

    public PubKeyRing getPubKeyRing() {
        return pubKeyRing;
    }

    // TODO refactor those out of Offer, offer should be pure value object
    @Nullable
    public Fiat getPrice() {
        if (useMarketBasedPrice) {
            checkNotNull(priceFeedService, "priceFeed must not be null");
            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
            if (marketPrice != null) {
                PriceFeedService.Type priceFeedType;
                double factor;
                if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                    priceFeedType = direction == Direction.BUY ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
                    factor = direction == Offer.Direction.SELL ? 1 - marketPriceMargin : 1 + marketPriceMargin;
                } else {
                    priceFeedType = direction == Direction.SELL ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
                    factor = direction == Offer.Direction.BUY ? 1 - marketPriceMargin : 1 + marketPriceMargin;
                }
                double marketPriceAsDouble = marketPrice.getPrice(priceFeedType);
                double targetPrice = marketPriceAsDouble * factor;
                if (CurrencyUtil.isCryptoCurrency(currencyCode))
                    targetPrice = targetPrice != 0 ? 1d / targetPrice : 0;
                try {
                    final double rounded = MathUtils.roundDouble(targetPrice, Fiat.SMALLEST_UNIT_EXPONENT);
                    return Fiat.parseFiat(currencyCode, decimalFormat.format(rounded).replace(",", "."));
                } catch (Exception e) {
                    log.error("Exception at getPrice / parseToFiat: " + e.toString() + "\n" +
                            "That case should never happen.");
                    return null;
                }
            } else {
                log.debug("We don't have a market price.\n" +
                        "That case could only happen if you don't have a price feed.");
                return null;
            }
        } else {
            return Fiat.valueOf(currencyCode, fiatPrice);
        }
    }

    // TODO refactor those out of Offer, offer should be pure value object
    public void checkTradePriceTolerance(long takersTradePrice) throws TradePriceOutOfToleranceException, MarketPriceNotAvailableException, IllegalArgumentException {
        checkArgument(takersTradePrice > 0, "takersTradePrice must be positive");
        Fiat tradePriceAsFiat = Fiat.valueOf(getCurrencyCode(), takersTradePrice);
        Fiat offerPriceAsFiat = getPrice();

        if (offerPriceAsFiat == null)
            throw new MarketPriceNotAvailableException("Market price required for calculating trade price is not available.");

        double factor = (double) takersTradePrice / (double) offerPriceAsFiat.value;
        // We allow max. 2 % difference between own offer price calculation and takers calculation.
        // Market price might be different at offerer's and takers side so we need a bit of tolerance.
        // The tolerance will get smaller once we have multiple price feeds avoiding fast price fluctuations
        // from one provider.
        if (Math.abs(1 - factor) > 0.02) {
            String msg = "Taker's trade price is too far away from our calculated price based on the market price.\n" +
                    "tradePriceAsFiat=" + tradePriceAsFiat.toFriendlyString() + "\n" +
                    "offerPriceAsFiat=" + offerPriceAsFiat.toFriendlyString();
            log.warn(msg);
            throw new TradePriceOutOfToleranceException(msg);
        }
    }

    public double getMarketPriceMargin() {
        return marketPriceMargin;
    }

    public boolean getUseMarketBasedPrice() {
        return useMarketBasedPrice;
    }

    public Coin getAmount() {
        return Coin.valueOf(amount);
    }

    public Coin getMinAmount() {
        return Coin.valueOf(minAmount);
    }

    public Direction getDirection() {
        return direction;
    }

    public Direction getMirroredDirection() {
        return direction == Direction.BUY ? Direction.SELL : Direction.BUY;
    }

    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.getPaymentMethodById(paymentMethodName);
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    @Nullable
    public String getCountryCode() {
        return countryCode;
    }

    @Nullable
    public List<String> getAcceptedCountryCodes() {
        return acceptedCountryCodes;
    }

    @Nullable
    public List<String> getAcceptedBankIds() {
        return acceptedBankIds;
    }

    @Nullable
    public String getBankId() {
        return bankId;
    }

    public String getOfferFeePaymentTxID() {
        return offerFeePaymentTxID;
    }

    public List<NodeAddress> getArbitratorNodeAddresses() {
        return arbitratorNodeAddresses;
    }

    public Date getDate() {
        return new Date(date);
    }

    public State getState() {
        return state;
    }

    public ObjectProperty<State> stateProperty() {
        return stateProperty;
    }

    public String getOffererPaymentAccountId() {
        return offererPaymentAccountId;
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }

    public String getVersionNr() {
        return versionNr;
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

    public long getMaxTradePeriod() {
        return maxTradePeriod;
    }

    public long getBlockHeightAtOfferCreation() {
        return blockHeightAtOfferCreation;
    }

    public boolean isUseAutoClose() {
        return useAutoClose;
    }

    public boolean isUseReOpenAfterAutoClose() {
        return useReOpenAfterAutoClose;
    }

    public long getLowerClosePrice() {
        return lowerClosePrice;
    }

    public long getUpperClosePrice() {
        return upperClosePrice;
    }

    public boolean isPrivateOffer() {
        return isPrivateOffer;
    }

    @Nullable
    public String getHashOfChallenge() {
        return hashOfChallenge;
    }

    @Nullable
    public Map<String, String> getExtraDataMap() {
        return extraDataMap;
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
            throw new RuntimeException("Offer is in invalid state: offerFeePaymentTxID is not set when adding to P2P network.");
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
