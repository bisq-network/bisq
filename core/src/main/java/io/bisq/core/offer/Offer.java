package io.bisq.core.offer;

import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.monetary.Altcoin;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.common.util.JsonExclude;
import io.bisq.common.util.MathUtils;
import io.bisq.common.util.Utilities;
import io.bisq.core.exceptions.TradePriceOutOfToleranceException;
import io.bisq.core.offer.availability.OfferAvailabilityModel;
import io.bisq.core.offer.availability.OfferAvailabilityProtocol;
import io.bisq.core.provider.price.MarketPrice;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.payload.crypto.PubKeyRing;
import io.bisq.protobuffer.payload.offer.OfferPayload;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Offer implements Serializable {

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

    @Getter
    private final OfferPayload offerPayload;
    @JsonExclude
    transient private Offer.State state = Offer.State.UNDEFINED;
    // Those state properties are transient and only used at runtime!
    // don't access directly as it might be null; use getStateProperty() which creates an object if not instantiated
    @JsonExclude
    @Getter
    transient private ObjectProperty<Offer.State> stateProperty = new SimpleObjectProperty<>(state);
    @JsonExclude
    @Nullable
    transient private OfferAvailabilityProtocol availabilityProtocol;
    @JsonExclude
    @Getter
    transient private StringProperty errorMessageProperty = new SimpleStringProperty();
    @JsonExclude
    @Setter
    @Nullable
    transient private PriceFeedService priceFeedService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(OfferPayload offerPayload) {
        this.offerPayload = offerPayload;
    }

    // TODO still needed as we get the offer from persistence serialized
    // can be removed once we have full PB support
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            stateProperty = new SimpleObjectProperty<>(Offer.State.UNDEFINED);

            // we don't need to fill it as the error message is only relevant locally, so we don't store it in the transmitted object
            errorMessageProperty = new SimpleStringProperty();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
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
        if (offerPayload.isUseMarketBasedPrice()) {
            checkNotNull(priceFeedService, "priceFeed must not be null");
            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
            if (marketPrice != null) {
                double factor;
                double marketPriceMargin = offerPayload.getMarketPriceMargin();
                if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                    factor = getDirection() == Offer.Direction.SELL ?
                            1 - marketPriceMargin : 1 + marketPriceMargin;
                } else {
                    factor = getDirection() == Offer.Direction.BUY ?
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
            return Price.valueOf(currencyCode, offerPayload.getPrice());
        }
    }

    public void checkTradePriceTolerance(long takersTradePrice) throws TradePriceOutOfToleranceException,
            MarketPriceNotAvailableException, IllegalArgumentException {
        checkArgument(takersTradePrice > 0, "takersTradePrice must be positive");
        Price tradePrice = Price.valueOf(getCurrencyCode(), takersTradePrice);
        Price offerPrice = getPrice();

        if (offerPrice == null)
            throw new MarketPriceNotAvailableException("Market price required for calculating trade price is not available.");

        double factor = (double) takersTradePrice / (double) offerPrice.getValue();
        // We allow max. 1 % difference between own offerPayload price calculation and takers calculation.
        // Market price might be different at offerer's and takers side so we need a bit of tolerance.
        // The tolerance will get smaller once we have multiple price feeds avoiding fast price fluctuations
        // from one provider.
        if (Math.abs(1 - factor) > 0.01) {
            String msg = "Taker's trade price is too far away from our calculated price based on the market price.\n" +
                    "tradePrice=" + tradePrice.getValue() + "\n" +
                    "offerPrice=" + offerPrice.getValue();
            log.warn(msg);
            throw new TradePriceOutOfToleranceException(msg);
        }
    }

    @Nullable
    public Volume getVolumeByAmount(Coin amount) {
        Price price = getPrice();
        if (price != null && amount != null) {
            // try {
            return price.getVolumeByAmount(amount);
           /* } catch (Throwable t) {
                log.error("getVolumeByAmount failed. Error=" + t.getMessage());
                return null;
            }*/
        } else {
            return null;
        }
    }

    public void resetState() {
        setState(Offer.State.UNDEFINED);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setState(Offer.State state) {
        this.state = state;
        stateProperty().set(state);
    }

    public ObjectProperty<Offer.State> stateProperty() {
        return stateProperty;
    }

    public void setOfferFeePaymentTxId(String offerFeePaymentTxID) {
        offerPayload.setOfferFeePaymentTxId(offerFeePaymentTxID);
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessageProperty.set(errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    // converted payload properties
    public Coin getTxFee() {
        return Coin.valueOf(offerPayload.getTxFee());
    }

    public Coin getCreateOfferFee() {
        return Coin.valueOf(offerPayload.getCreateOfferFee());
    }

    public Coin getBuyerSecurityDeposit() {
        return Coin.valueOf(offerPayload.getBuyerSecurityDeposit());
    }

    public Coin getSellerSecurityDeposit() {
        return Coin.valueOf(offerPayload.getSellerSecurityDeposit());
    }

    public Coin getMaxTradeLimit() {
        return Coin.valueOf(offerPayload.getMaxTradeLimit());
    }

    public Coin getAmount() {
        return Coin.valueOf(offerPayload.getAmount());
    }

    public Coin getMinAmount() {
        return Coin.valueOf(offerPayload.getMinAmount());
    }

    public Date getDate() {
        return new Date(offerPayload.getDate());
    }

    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.getPaymentMethodById(offerPayload.getPaymentMethodId());
    }

    // utils
    public String getShortId() {
        return Utilities.getShortId(offerPayload.getId());
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
        return getDirection() == Offer.Direction.BUY;
    }

    public Offer.Direction getMirroredDirection() {
        return getDirection() == Offer.Direction.BUY ? Offer.Direction.SELL : Offer.Direction.BUY;
    }

    public boolean isMyOffer(KeyRing keyRing) {
        return getPubKeyRing().equals(keyRing.getPubKeyRing());
    }


    // domain properties
    public Offer.State getState() {
        return state;
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegate Getter (boilerplate code generated via IntelliJ generate delegte feature)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer.Direction getDirection() {
        return Offer.Direction.valueOf(offerPayload.getDirection().name());
    }

    public String getId() {
        return offerPayload.getId();
    }

    public List<NodeAddress> getArbitratorNodeAddresses() {
        return offerPayload.getArbitratorNodeAddresses();
    }

    @Nullable
    public List<String> getAcceptedBankIds() {
        return offerPayload.getAcceptedBankIds();
    }

    @Nullable
    public String getBankId() {
        return offerPayload.getBankId();
    }

    @Nullable
    public List<String> getAcceptedCountryCodes() {
        return offerPayload.getAcceptedCountryCodes();
    }

    @Nullable
    public String getCountryCode() {
        return offerPayload.getCountryCode();
    }

    public String getCurrencyCode() {
        return CurrencyUtil.isCryptoCurrency(offerPayload.getBaseCurrencyCode()) ?
                offerPayload.getBaseCurrencyCode() :
                offerPayload.getCounterCurrencyCode();
    }

    public long getProtocolVersion() {
        return offerPayload.getProtocolVersion();
    }

    public boolean isUseMarketBasedPrice() {
        return offerPayload.isUseMarketBasedPrice();
    }

    public double getMarketPriceMargin() {
        return offerPayload.getMarketPriceMargin();
    }

    public NodeAddress getOffererNodeAddress() {
        return offerPayload.getOffererNodeAddress();
    }

    public PubKeyRing getPubKeyRing() {
        return offerPayload.getPubKeyRing();
    }

    public String getOffererPaymentAccountId() {
        return offerPayload.getOffererPaymentAccountId();
    }

    public String getOfferFeePaymentTxId() {
        return offerPayload.getOfferFeePaymentTxId();
    }

    public String getVersionNr() {
        return offerPayload.getVersionNr();
    }

    public long getMaxTradePeriod() {
        return offerPayload.getMaxTradePeriod();
    }

    public NodeAddress getOwnerNodeAddress() {
        return offerPayload.getOwnerNodeAddress();
    }

    // Yet unused
    public PublicKey getOwnerPubKey() {
        return offerPayload.getOwnerPubKey();
    }

    @Nullable
    public Map<String, String> getExtraDataMap() {
        return offerPayload.getExtraDataMap();
    }

    public boolean isUseAutoClose() {
        return offerPayload.isUseAutoClose();
    }

    public long getBlockHeightAtOfferCreation() {
        return offerPayload.getBlockHeightAtOfferCreation();
    }

    @Nullable
    public String getHashOfChallenge() {
        return offerPayload.getHashOfChallenge();
    }

    public boolean isPrivateOffer() {
        return offerPayload.isPrivateOffer();
    }

    public long getUpperClosePrice() {
        return offerPayload.getUpperClosePrice();
    }

    public long getLowerClosePrice() {
        return offerPayload.getLowerClosePrice();
    }

    public boolean isUseReOpenAfterAutoClose() {
        return offerPayload.isUseReOpenAfterAutoClose();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Offer offer = (Offer) o;

        if (offerPayload != null ? !offerPayload.equals(offer.offerPayload) : offer.offerPayload != null) return false;
        if (state != offer.state) return false;
        return !(errorMessageProperty != null ? !errorMessageProperty.equals(offer.errorMessageProperty) : offer.errorMessageProperty != null);

    }

    @Override
    public int hashCode() {
        int result = offerPayload != null ? offerPayload.hashCode() : 0;
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (errorMessageProperty != null ? errorMessageProperty.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Offer{" +
                "offerPayload=" + offerPayload +
                ", state=" + state +
                ", errorMessageProperty=" + errorMessageProperty +
                '}';
    }
}
