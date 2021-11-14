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

package bisq.core.offer;

import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.availability.OfferAvailabilityModel;
import bisq.core.offer.availability.OfferAvailabilityProtocol;
import bisq.core.offer.bisq_v1.MarketPriceNotAvailableException;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.util.VolumeUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.JsonExclude;
import bisq.common.util.MathUtils;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.security.PublicKey;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Offer implements NetworkPayload, PersistablePayload {

    // We allow max. 1 % difference between own offerPayload price calculation and takers calculation.
    // Market price might be different at maker's and takers side so we need a bit of tolerance.
    // The tolerance will get smaller once we have multiple price feeds avoiding fast price fluctuations
    // from one provider.
    private final static double PRICE_TOLERANCE = 0.01;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum State {
        UNKNOWN,
        OFFER_FEE_PAID,
        AVAILABLE,
        NOT_AVAILABLE,
        REMOVED,
        MAKER_OFFLINE
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    private final OfferPayloadBase offerPayloadBase;
    @JsonExclude
    @Getter
    final transient private ObjectProperty<Offer.State> stateProperty = new SimpleObjectProperty<>(Offer.State.UNKNOWN);
    @JsonExclude
    @Nullable
    transient private OfferAvailabilityProtocol availabilityProtocol;
    @JsonExclude
    @Getter
    final transient private StringProperty errorMessageProperty = new SimpleStringProperty();
    @JsonExclude
    @Nullable
    @Setter
    transient private PriceFeedService priceFeedService;

    // Used only as cache
    @Nullable
    @JsonExclude
    transient private String currencyCode;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(OfferPayloadBase offerPayloadBase) {
        this.offerPayloadBase = offerPayloadBase;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.Offer toProtoMessage() {
        if (isBsqSwapOffer()) {
            return protobuf.Offer.newBuilder().setBsqSwapOfferPayload(((BsqSwapOfferPayload) offerPayloadBase)
                    .toProtoMessage().getBsqSwapOfferPayload()).build();
        } else {
            return protobuf.Offer.newBuilder().setOfferPayload(((OfferPayload) offerPayloadBase)
                    .toProtoMessage().getOfferPayload()).build();
        }
    }

    public static Offer fromProto(protobuf.Offer proto) {
        if (proto.hasOfferPayload()) {
            return new Offer(OfferPayload.fromProto(proto.getOfferPayload()));
        } else {
            return new Offer(BsqSwapOfferPayload.fromProto(proto.getBsqSwapOfferPayload()));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Availability
    ///////////////////////////////////////////////////////////////////////////////////////////

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

    public void cancelAvailabilityRequest() {
        if (availabilityProtocol != null)
            availabilityProtocol.cancel();
    }

    @Nullable
    public Price getPrice() {
        String currencyCode = getCurrencyCode();
        Optional<OfferPayload> optionalOfferPayload = getOfferPayload();
        if (!optionalOfferPayload.isPresent()) {
            return Price.valueOf(currencyCode, offerPayloadBase.getPrice());
        }

        OfferPayload offerPayload = optionalOfferPayload.get();
        if (!offerPayload.isUseMarketBasedPrice()) {
            return Price.valueOf(currencyCode, offerPayloadBase.getPrice());
        }

        checkNotNull(priceFeedService, "priceFeed must not be null");
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        if (marketPrice != null && marketPrice.isRecentExternalPriceAvailable()) {
            double factor;
            double marketPriceMargin = offerPayload.getMarketPriceMargin();
            if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                factor = getDirection() == OfferDirection.SELL ?
                        1 - marketPriceMargin : 1 + marketPriceMargin;
            } else {
                factor = getDirection() == OfferDirection.BUY ?
                        1 - marketPriceMargin : 1 + marketPriceMargin;
            }
            double marketPriceAsDouble = marketPrice.getPrice();
            double targetPriceAsDouble = marketPriceAsDouble * factor;
            try {
                int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                        Altcoin.SMALLEST_UNIT_EXPONENT :
                        Fiat.SMALLEST_UNIT_EXPONENT;
                double scaled = MathUtils.scaleUpByPowerOf10(targetPriceAsDouble, precision);
                final long roundedToLong = MathUtils.roundDoubleToLong(scaled);
                return Price.valueOf(currencyCode, roundedToLong);
            } catch (Exception e) {
                log.error("Exception at getPrice / parseToFiat: " + e + "\n" +
                        "That case should never happen.");
                return null;
            }
        } else {
            log.trace("We don't have a market price. " +
                    "That case could only happen if you don't have a price feed.");
            return null;
        }
    }

    public long getFixedPrice() {
        return offerPayloadBase.getPrice();
    }

    public void checkTradePriceTolerance(long takersTradePrice) throws TradePriceOutOfToleranceException,
            MarketPriceNotAvailableException, IllegalArgumentException {
        if (!isUseMarketBasedPrice()) {
            checkArgument(takersTradePrice == getFixedPrice(),
                    "Takers price does not match offer price");
        }

        Price tradePrice = Price.valueOf(getCurrencyCode(), takersTradePrice);
        Price offerPrice = getPrice();
        if (offerPrice == null)
            throw new MarketPriceNotAvailableException("Market price required for calculating trade price is not available.");

        checkArgument(takersTradePrice > 0, "takersTradePrice must be positive");

        double relation = (double) takersTradePrice / (double) offerPrice.getValue();
        // We allow max. 2 % difference between own offerPayload price calculation and takers calculation.
        // Market price might be different at maker's and takers side so we need a bit of tolerance.
        // The tolerance will get smaller once we have multiple price feeds avoiding fast price fluctuations
        // from one provider.

        double deviation = Math.abs(1 - relation);
        log.info("Price at take-offer time: id={}, currency={}, takersPrice={}, makersPrice={}, deviation={}",
                getShortId(), getCurrencyCode(), takersTradePrice, offerPrice.getValue(),
                deviation * 100 + "%");
        if (deviation > PRICE_TOLERANCE) {
            String msg = "Taker's trade price is too far away from our calculated price based on the market price.\n" +
                    "takersPrice=" + tradePrice.getValue() + "\n" +
                    "makersPrice=" + offerPrice.getValue();
            log.warn(msg);
            throw new TradePriceOutOfToleranceException(msg);
        }
    }

    @Nullable
    public Volume getVolumeByAmount(Coin amount) {
        Price price = getPrice();
        if (price == null || amount == null) {
            return null;
        }
        Volume volumeByAmount = price.getVolumeByAmount(amount);
        if (offerPayloadBase.getPaymentMethodId().equals(PaymentMethod.HAL_CASH_ID))
            volumeByAmount = VolumeUtil.getAdjustedVolumeForHalCash(volumeByAmount);
        else if (CurrencyUtil.isFiatCurrency(offerPayloadBase.getCurrencyCode()))
            volumeByAmount = VolumeUtil.getRoundedFiatVolume(volumeByAmount);

        return volumeByAmount;
    }

    public void resetState() {
        setState(Offer.State.UNKNOWN);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setState(Offer.State state) {
        stateProperty().set(state);
    }

    public ObjectProperty<Offer.State> stateProperty() {
        return stateProperty;
    }

    public void setOfferFeePaymentTxId(String offerFeePaymentTxID) {
        getOfferPayload().ifPresent(p -> p.setOfferFeePaymentTxId(offerFeePaymentTxID));
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessageProperty.set(errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    // converted payload properties
    public Coin getTxFee() {
        return Coin.valueOf(getOfferPayload().map(OfferPayload::getTxFee).orElse(0L));
    }

    public Coin getMakerFee() {
        return getOfferPayload().map(OfferPayload::getMakerFee).map(Coin::valueOf).orElse(Coin.ZERO);
    }

    public boolean isCurrencyForMakerFeeBtc() {
        return getOfferPayload().map(OfferPayload::isCurrencyForMakerFeeBtc).orElse(false);
    }

    public Coin getBuyerSecurityDeposit() {
        return Coin.valueOf(getOfferPayload().map(OfferPayload::getBuyerSecurityDeposit).orElse(0L));
    }

    public Coin getSellerSecurityDeposit() {
        return Coin.valueOf(getOfferPayload().map(OfferPayload::getSellerSecurityDeposit).orElse(0L));
    }

    public Coin getMaxTradeLimit() {
        return getOfferPayload().map(OfferPayload::getMaxTradeLimit).map(Coin::valueOf).orElse(Coin.ZERO);
    }

    public Coin getAmount() {
        return Coin.valueOf(offerPayloadBase.getAmount());
    }

    public Coin getMinAmount() {
        return Coin.valueOf(offerPayloadBase.getMinAmount());
    }

    public boolean isRange() {
        return offerPayloadBase.getAmount() != offerPayloadBase.getMinAmount();
    }

    public Date getDate() {
        return new Date(offerPayloadBase.getDate());
    }

    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.getPaymentMethodById(offerPayloadBase.getPaymentMethodId());
    }

    // utils
    public String getShortId() {
        return Utilities.getShortId(offerPayloadBase.getId());
    }

    @Nullable
    public Volume getVolume() {
        return getVolumeByAmount(getAmount());
    }

    @Nullable
    public Volume getMinVolume() {
        return getVolumeByAmount(getMinAmount());
    }

    public boolean isBuyOffer() {
        return getDirection() == OfferDirection.BUY;
    }

    public OfferDirection getMirroredDirection() {
        return getDirection() == OfferDirection.BUY ? OfferDirection.SELL : OfferDirection.BUY;
    }

    public boolean isMyOffer(KeyRing keyRing) {
        return getPubKeyRing().equals(keyRing.getPubKeyRing());
    }

    public Optional<String> getAccountAgeWitnessHashAsHex() {
        Map<String, String> extraDataMap = getExtraDataMap();
        if (extraDataMap != null && extraDataMap.containsKey(OfferPayload.ACCOUNT_AGE_WITNESS_HASH))
            return Optional.of(extraDataMap.get(OfferPayload.ACCOUNT_AGE_WITNESS_HASH));
        else
            return Optional.empty();
    }

    public String getF2FCity() {
        if (getExtraDataMap() != null && getExtraDataMap().containsKey(OfferPayload.F2F_CITY))
            return getExtraDataMap().get(OfferPayload.F2F_CITY);
        else
            return "";
    }

    public String getExtraInfo() {
        if (getExtraDataMap() != null && getExtraDataMap().containsKey(OfferPayload.F2F_EXTRA_INFO))
            return getExtraDataMap().get(OfferPayload.F2F_EXTRA_INFO);
        else if (getExtraDataMap() != null && getExtraDataMap().containsKey(OfferPayload.CASH_BY_MAIL_EXTRA_INFO))
            return getExtraDataMap().get(OfferPayload.CASH_BY_MAIL_EXTRA_INFO);
        else
            return "";
    }

    public String getPaymentMethodNameWithCountryCode() {
        String method = this.getPaymentMethod().getShortName();
        String methodCountryCode = this.getCountryCode();
        if (methodCountryCode != null)
            method = method + " (" + methodCountryCode + ")";
        return method;
    }

    // domain properties
    public Offer.State getState() {
        return stateProperty.get();
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }

    public String getErrorMessage() {
        return errorMessageProperty.get();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegate Getter (boilerplate code generated via IntelliJ generate delegate feature)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferDirection getDirection() {
        return offerPayloadBase.getDirection();
    }

    public String getId() {
        return offerPayloadBase.getId();
    }

    @Nullable
    public List<String> getAcceptedBankIds() {
        return getOfferPayload().map(OfferPayload::getAcceptedBankIds).orElse(null);
    }

    @Nullable
    public String getBankId() {
        return getOfferPayload().map(OfferPayload::getBankId).orElse(null);
    }

    @Nullable
    public List<String> getAcceptedCountryCodes() {
        return getOfferPayload().map(OfferPayload::getAcceptedCountryCodes).orElse(null);
    }

    @Nullable
    public String getCountryCode() {
        return getOfferPayload().map(OfferPayload::getCountryCode).orElse(null);
    }

    public String getCurrencyCode() {
        if (currencyCode != null) {
            return currencyCode;
        }

        currencyCode = getBaseCurrencyCode().equals("BTC") ?
                getCounterCurrencyCode() :
                getBaseCurrencyCode();
        return currencyCode;
    }

    public String getCounterCurrencyCode() {
        return offerPayloadBase.getCounterCurrencyCode();
    }

    public String getBaseCurrencyCode() {
        return offerPayloadBase.getBaseCurrencyCode();
    }

    public String getPaymentMethodId() {
        return offerPayloadBase.getPaymentMethodId();
    }

    public long getProtocolVersion() {
        return offerPayloadBase.getProtocolVersion();
    }

    public boolean isUseMarketBasedPrice() {
        return getOfferPayload().map(OfferPayload::isUseMarketBasedPrice).orElse(false);
    }

    public double getMarketPriceMargin() {
        return getOfferPayload().map(OfferPayload::getMarketPriceMargin).orElse(0D);
    }

    public NodeAddress getMakerNodeAddress() {
        return offerPayloadBase.getOwnerNodeAddress();
    }

    public PubKeyRing getPubKeyRing() {
        return offerPayloadBase.getPubKeyRing();
    }

    public String getMakerPaymentAccountId() {
        return offerPayloadBase.getMakerPaymentAccountId();
    }

    public String getOfferFeePaymentTxId() {
        return getOfferPayload().map(OfferPayload::getOfferFeePaymentTxId).orElse(null);
    }

    public String getVersionNr() {
        return offerPayloadBase.getVersionNr();
    }

    public long getMaxTradePeriod() {
        return getOfferPayload().map(OfferPayload::getMaxTradePeriod).orElse(0L);
    }

    public NodeAddress getOwnerNodeAddress() {
        return offerPayloadBase.getOwnerNodeAddress();
    }

    // Yet unused
    public PublicKey getOwnerPubKey() {
        return offerPayloadBase.getOwnerPubKey();
    }

    @Nullable
    public Map<String, String> getExtraDataMap() {
        return offerPayloadBase.getExtraDataMap();
    }

    public boolean isUseAutoClose() {
        return getOfferPayload().map(OfferPayload::isUseAutoClose).orElse(false);
    }

    public boolean isUseReOpenAfterAutoClose() {
        return getOfferPayload().map(OfferPayload::isUseReOpenAfterAutoClose).orElse(false);
    }

    public boolean isBsqSwapOffer() {
        return getOfferPayloadBase() instanceof BsqSwapOfferPayload;
    }

    public boolean isXmrAutoConf() {
        if (!isXmr()) {
            return false;
        }
        if (getExtraDataMap() == null || !getExtraDataMap().containsKey(OfferPayload.XMR_AUTO_CONF)) {
            return false;
        }

        return getExtraDataMap().get(OfferPayload.XMR_AUTO_CONF).equals(OfferPayload.XMR_AUTO_CONF_ENABLED_VALUE);
    }

    public boolean isXmr() {
        return getCurrencyCode().equals("XMR");
    }

    public Optional<OfferPayload> getOfferPayload() {
        if (offerPayloadBase instanceof OfferPayload) {
            return Optional.of((OfferPayload) offerPayloadBase);
        }
        return Optional.empty();
    }

    public Optional<BsqSwapOfferPayload> getBsqSwapOfferPayload() {
        if (offerPayloadBase instanceof BsqSwapOfferPayload) {
            return Optional.of((BsqSwapOfferPayload) offerPayloadBase);
        }
        return Optional.empty();
    }

    public byte[] getOfferPayloadHash() {
        return offerPayloadBase.getHash();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Offer offer = (Offer) o;

        if (offerPayloadBase != null ? !offerPayloadBase.equals(offer.offerPayloadBase) : offer.offerPayloadBase != null)
            return false;
        //noinspection SimplifiableIfStatement
        if (getState() != offer.getState()) return false;
        return !(getErrorMessage() != null ? !getErrorMessage().equals(offer.getErrorMessage()) : offer.getErrorMessage() != null);

    }

    @Override
    public int hashCode() {
        int result = offerPayloadBase != null ? offerPayloadBase.hashCode() : 0;
        result = 31 * result + (getState() != null ? getState().hashCode() : 0);
        result = 31 * result + (getErrorMessage() != null ? getErrorMessage().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Offer{" +
                "getErrorMessage()='" + getErrorMessage() + '\'' +
                ", state=" + getState() +
                ", offerPayloadBase=" + offerPayloadBase +
                '}';
    }
}
