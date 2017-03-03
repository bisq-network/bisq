/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.offer;

import io.bitsquare.app.DevFlags;
import io.bitsquare.app.Version;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.btc.provider.price.MarketPrice;
import io.bitsquare.btc.provider.price.PriceFeedService;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.common.util.JsonExclude;
import io.bitsquare.common.util.MathUtils;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.storage.payload.RequiresOwnerIsOnlinePayload;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.exceptions.MarketPriceNotAvailableException;
import io.bitsquare.trade.exceptions.TradePriceOutOfToleranceException;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityModel;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityProtocol;
import javafx.beans.property.*;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.PublicKey;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
    public static final long TTL = TimeUnit.MINUTES.toMillis(DevFlags.STRESS_TEST_MODE ? 6 : 6);
    public final static String TAC_OFFERER = "With placing that offer I agree to trade " +
            "with any trader who fulfills the conditions as defined above.";
    public static final String TAC_TAKER = "With taking that offer I agree to the trade conditions as defined above.";


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
    private final ArrayList<String> acceptedCountryCodes;
    @Nullable
    private final String bankId;
    @Nullable
    private final ArrayList<String> acceptedBankIds;
    private final ArrayList<NodeAddress> arbitratorNodeAddresses;


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
    private final long securityDeposit;
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
    private HashMap<String, String> extraDataMap;

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

    public Offer(String id,
                 NodeAddress offererNodeAddress,
                 PubKeyRing pubKeyRing,
                 Direction direction,
                 long fiatPrice,
                 double marketPriceMargin,
                 boolean useMarketBasedPrice,
                 long amount,
                 long minAmount,
                 String currencyCode,
                 ArrayList<NodeAddress> arbitratorNodeAddresses,
                 String paymentMethodName,
                 String offererPaymentAccountId,
                 @Nullable String countryCode,
                 @Nullable ArrayList<String> acceptedCountryCodes,
                 @Nullable String bankId,
                 @Nullable ArrayList<String> acceptedBankIds,
                 PriceFeedService priceFeedService,
                 String versionNr,
                 long blockHeightAtOfferCreation,
                 long txFee,
                 long createOfferFee,
                 long securityDeposit,
                 long maxTradeLimit,
                 long maxTradePeriod,
                 boolean useAutoClose,
                 boolean useReOpenAfterAutoClose,
                 long lowerClosePrice,
                 long upperClosePrice,
                 boolean isPrivateOffer,
                 @Nullable String hashOfChallenge,
                 @Nullable HashMap<String, String> extraDataMap) {

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
        this.countryCode = countryCode;
        this.acceptedCountryCodes = acceptedCountryCodes;
        this.bankId = bankId;
        this.acceptedBankIds = acceptedBankIds;
        this.priceFeedService = priceFeedService;
        this.versionNr = versionNr;
        this.blockHeightAtOfferCreation = blockHeightAtOfferCreation;
        this.txFee = txFee;
        this.createOfferFee = createOfferFee;
        this.securityDeposit = securityDeposit;
        this.maxTradeLimit = maxTradeLimit;
        this.maxTradePeriod = maxTradePeriod;
        this.useAutoClose = useAutoClose;
        this.useReOpenAfterAutoClose = useReOpenAfterAutoClose;
        this.lowerClosePrice = lowerClosePrice;
        this.upperClosePrice = upperClosePrice;
        this.isPrivateOffer = isPrivateOffer;
        this.hashOfChallenge = hashOfChallenge;
        this.extraDataMap = extraDataMap;

        protocolVersion = Version.TRADE_PROTOCOL_VERSION;

        date = new Date().getTime();
        setState(State.UNDEFINED);
        decimalFormat = new DecimalFormat("#.#");
        decimalFormat.setMaximumFractionDigits(Fiat.SMALLEST_UNIT_EXPONENT);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            stateProperty = new SimpleObjectProperty<>(State.UNDEFINED);

            // we don't need to fill it as the error message is only relevant locally, so we don't store it in the transmitted object
            errorMessageProperty = new SimpleStringProperty();
            decimalFormat = new DecimalFormat("#.#");
            decimalFormat.setMaximumFractionDigits(Fiat.SMALLEST_UNIT_EXPONENT);
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    @Override
    public NodeAddress getOwnerNodeAddress() {
        return offererNodeAddress;
    }

    //TODO update with new properties
    public void validate() {
        checkNotNull(getAmount(), "Amount is null");
        checkNotNull(getArbitratorNodeAddresses(), "Arbitrator is null");
        checkNotNull(getDate(), "CreationDate is null");
        checkNotNull(getCurrencyCode(), "Currency is null");
        checkNotNull(getDirection(), "Direction is null");
        checkNotNull(getId(), "Id is null");
        checkNotNull(getPubKeyRing(), "pubKeyRing is null");
        checkNotNull(getMinAmount(), "MinAmount is null");
        checkNotNull(getPrice(), "Price is null");
        checkNotNull(getTxFee(), "txFee is null");
        checkNotNull(getCreateOfferFee(), "CreateOfferFee is null");
        checkNotNull(getVersionNr(), "VersionNr is null");
        checkNotNull(getSecurityDeposit(), "SecurityDeposit is null");
        checkNotNull(getMaxTradeLimit(), "MaxTradeLimit is null");
        checkArgument(getMaxTradePeriod() > 0, "maxTradePeriod is 0 or negative. maxTradePeriod=" + getMaxTradePeriod());

        checkArgument(getMinAmount().compareTo(Restrictions.MIN_TRADE_AMOUNT) >= 0, "MinAmount is less then "
                + Restrictions.MIN_TRADE_AMOUNT.toFriendlyString());
        checkArgument(getAmount().compareTo(getPaymentMethod().getMaxTradeLimit()) <= 0, "Amount is larger then "
                + getPaymentMethod().getMaxTradeLimit().toFriendlyString());
        checkArgument(getAmount().compareTo(getMinAmount()) >= 0, "MinAmount is larger then Amount");

        checkArgument(getPrice().isPositive(), "Price is not a positive value");
        // TODO check upper and lower bounds for fiat
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

    public Coin getSecurityDeposit() {
        return Coin.valueOf(securityDeposit);
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
    public HashMap<String, String> getExtraDataMap() {
        return extraDataMap;
    }

    //TODO update with new properties
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Offer offer = (Offer) o;

        if (date != offer.date) return false;
        if (protocolVersion != offer.protocolVersion) return false;
        if (useMarketBasedPrice != offer.useMarketBasedPrice) return false;
        if (fiatPrice != offer.fiatPrice) return false;
        if (Double.compare(offer.marketPriceMargin, marketPriceMargin) != 0) return false;
        if (amount != offer.amount) return false;
        if (minAmount != offer.minAmount) return false;
        if (txFee != offer.txFee) return false;
        if (createOfferFee != offer.createOfferFee) return false;
        if (securityDeposit != offer.securityDeposit) return false;
        if (maxTradePeriod != offer.maxTradePeriod) return false;
        if (maxTradeLimit != offer.maxTradeLimit) return false;
        if (isPrivateOffer != offer.isPrivateOffer) return false;
        if (direction != offer.direction) return false;
        if (currencyCode != null ? !currencyCode.equals(offer.currencyCode) : offer.currencyCode != null) return false;
        if (paymentMethodName != null ? !paymentMethodName.equals(offer.paymentMethodName) : offer.paymentMethodName != null)
            return false;
        if (countryCode != null ? !countryCode.equals(offer.countryCode) : offer.countryCode != null) return false;
        if (acceptedCountryCodes != null ? !acceptedCountryCodes.equals(offer.acceptedCountryCodes) : offer.acceptedCountryCodes != null)
            return false;
        if (bankId != null ? !bankId.equals(offer.bankId) : offer.bankId != null) return false;
        if (acceptedBankIds != null ? !acceptedBankIds.equals(offer.acceptedBankIds) : offer.acceptedBankIds != null)
            return false;
        if (arbitratorNodeAddresses != null ? !arbitratorNodeAddresses.equals(offer.arbitratorNodeAddresses) : offer.arbitratorNodeAddresses != null)
            return false;
        if (id != null ? !id.equals(offer.id) : offer.id != null) return false;
        if (offererNodeAddress != null ? !offererNodeAddress.equals(offer.offererNodeAddress) : offer.offererNodeAddress != null)
            return false;
        if (pubKeyRing != null ? !pubKeyRing.equals(offer.pubKeyRing) : offer.pubKeyRing != null) return false;
        if (offererPaymentAccountId != null ? !offererPaymentAccountId.equals(offer.offererPaymentAccountId) : offer.offererPaymentAccountId != null)
            return false;
        if (offerFeePaymentTxID != null ? !offerFeePaymentTxID.equals(offer.offerFeePaymentTxID) : offer.offerFeePaymentTxID != null)
            return false;
        if (versionNr != null ? !versionNr.equals(offer.versionNr) : offer.versionNr != null) return false;
        if (hashOfChallenge != null ? !hashOfChallenge.equals(offer.hashOfChallenge) : offer.hashOfChallenge != null)
            return false;
        return !(extraDataMap != null ? !extraDataMap.equals(offer.extraDataMap) : offer.extraDataMap != null);

    }

    //TODO update with new properties
    @Override
    public int hashCode() {
        int result;
        long temp;
        result = direction != null ? direction.hashCode() : 0;
        result = 31 * result + (currencyCode != null ? currencyCode.hashCode() : 0);
        result = 31 * result + (paymentMethodName != null ? paymentMethodName.hashCode() : 0);
        result = 31 * result + (countryCode != null ? countryCode.hashCode() : 0);
        result = 31 * result + (acceptedCountryCodes != null ? acceptedCountryCodes.hashCode() : 0);
        result = 31 * result + (bankId != null ? bankId.hashCode() : 0);
        result = 31 * result + (acceptedBankIds != null ? acceptedBankIds.hashCode() : 0);
        result = 31 * result + (arbitratorNodeAddresses != null ? arbitratorNodeAddresses.hashCode() : 0);
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (int) (date ^ (date >>> 32));
        result = 31 * result + (int) (protocolVersion ^ (protocolVersion >>> 32));
        result = 31 * result + (useMarketBasedPrice ? 1 : 0);
        result = 31 * result + (int) (fiatPrice ^ (fiatPrice >>> 32));
        temp = Double.doubleToLongBits(marketPriceMargin);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (amount ^ (amount >>> 32));
        result = 31 * result + (int) (minAmount ^ (minAmount >>> 32));
        result = 31 * result + (offererNodeAddress != null ? offererNodeAddress.hashCode() : 0);
        result = 31 * result + (pubKeyRing != null ? pubKeyRing.hashCode() : 0);
        result = 31 * result + (offererPaymentAccountId != null ? offererPaymentAccountId.hashCode() : 0);
        result = 31 * result + (offerFeePaymentTxID != null ? offerFeePaymentTxID.hashCode() : 0);
        result = 31 * result + (versionNr != null ? versionNr.hashCode() : 0);
        result = 31 * result + (int) (txFee ^ (txFee >>> 32));
        result = 31 * result + (int) (createOfferFee ^ (createOfferFee >>> 32));
        result = 31 * result + (int) (securityDeposit ^ (securityDeposit >>> 32));
        result = 31 * result + (int) (maxTradePeriod ^ (maxTradePeriod >>> 32));
        result = 31 * result + (int) (maxTradeLimit ^ (maxTradeLimit >>> 32));
        result = 31 * result + (isPrivateOffer ? 1 : 0);
        result = 31 * result + (hashOfChallenge != null ? hashOfChallenge.hashCode() : 0);
        result = 31 * result + (extraDataMap != null ? extraDataMap.hashCode() : 0);
        return result;
    }

    //TODO update with new properties
    @Override
    public String toString() {
        return "Offer{" +
                "\n\tid='" + getId() + '\'' +
                "\n\tversionNr=" + versionNr +
                "\n\tdirection=" + direction +
                "\n\tcurrencyCode='" + currencyCode + '\'' +
                "\n\tdate=" + new Date(date) +
                "\n\tdateAsTime=" + date +
                "\n\tfiatPrice=" + fiatPrice +
                "\n\tmarketPriceMargin=" + marketPriceMargin +
                "\n\tuseMarketBasedPrice=" + useMarketBasedPrice +
                "\n\tamount=" + amount +
                "\n\tminAmount=" + minAmount +

                "\n\ttxFee=" + txFee +
                "\n\tcreateOfferFee=" + createOfferFee +
                "\n\tsecurityDeposit=" + securityDeposit +
                "\n\tmaxTradePeriod=" + maxTradePeriod +
                "\n\tmaxTradeLimit=" + maxTradeLimit +
                "\n\tisPrivateOffer=" + isPrivateOffer +
                "\n\thashOfChallenge=" + hashOfChallenge +
                "\n\textraDataMap=" + (extraDataMap != null ? extraDataMap.toString() : "null") +

                "\n\toffererAddress=" + offererNodeAddress +
                "\n\tpubKeyRing=" + pubKeyRing +
                "\n\tpaymentMethodName='" + paymentMethodName + '\'' +
                "\n\tpaymentMethodCountryCode='" + countryCode + '\'' +
                "\n\toffererPaymentAccountId='" + offererPaymentAccountId + '\'' +
                "\n\tacceptedCountryCodes=" + acceptedCountryCodes +
                "\n\tbankId=" + bankId +
                "\n\tacceptedBanks=" + acceptedBankIds +
                "\n\tarbitratorAddresses=" + arbitratorNodeAddresses +
                "\n\tofferFeePaymentTxID='" + offerFeePaymentTxID + '\'' +
                "\n\tstate=" + state +
                "\n\tstateProperty=" + stateProperty +
                "\n\tavailabilityProtocol=" + availabilityProtocol +
                "\n\terrorMessageProperty=" + errorMessageProperty +
                "\n\tTAC_OFFERER=" + TAC_OFFERER +
                "\n\tTAC_TAKER=" + TAC_TAKER +
                "\n\thashCode=" + hashCode() +
                '}';
    }
}
