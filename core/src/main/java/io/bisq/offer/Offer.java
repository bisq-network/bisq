package io.bisq.offer;

import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.JsonExclude;
import io.bisq.common.util.MathUtils;
import io.bisq.crypto.KeyRing;
import io.bisq.exceptions.TradePriceOutOfToleranceException;
import io.bisq.locale.CurrencyUtil;
import io.bisq.offer.availability.OfferAvailabilityModel;
import io.bisq.offer.availability.OfferAvailabilityProtocol;
import io.bisq.payload.NodeAddress;
import io.bisq.payload.crypto.PubKeyRing;
import io.bisq.payload.offer.OfferPayload;
import io.bisq.payload.payment.PaymentMethod;
import io.bisq.provider.price.MarketPrice;
import io.bisq.provider.price.PriceFeedService;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Offer implements Serializable {

    @Getter
    private OfferPayload offerPayload;
    @JsonExclude
    transient private OfferPayload.State state = OfferPayload.State.UNDEFINED;
    // Those state properties are transient and only used at runtime!
    // don't access directly as it might be null; use getStateProperty() which creates an object if not instantiated
    @JsonExclude
    @Getter
    transient private ObjectProperty<OfferPayload.State> stateProperty = new SimpleObjectProperty<>(state);
    @JsonExclude
    @Nullable
    transient private OfferAvailabilityProtocol availabilityProtocol;
    @JsonExclude
    @Getter
    transient private StringProperty errorMessageProperty = new SimpleStringProperty();
    @JsonExclude
    @Getter
    @Setter
    transient private PriceFeedService priceFeedService;
    @JsonExclude
    transient private DecimalFormat decimalFormat;

    public Offer(OfferPayload offerPayload) {
        this.offerPayload = offerPayload;
        setState(OfferPayload.State.UNDEFINED);
        stateProperty = new SimpleObjectProperty<>(OfferPayload.State.UNDEFINED);

        // we don't need to fill it as the error message is only relevant locally, so we don't store it in the transmitted object
        errorMessageProperty = new SimpleStringProperty();
        decimalFormat = new DecimalFormat("#.#");
        decimalFormat.setMaximumFractionDigits(Fiat.SMALLEST_UNIT_EXPONENT);
    }

    // TODO still needed as we get the offer from persiistance 9serialized)
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            stateProperty = new SimpleObjectProperty<>(OfferPayload.State.UNDEFINED);

            // we don't need to fill it as the error message is only relevant locally, so we don't store it in the transmitted object
            errorMessageProperty = new SimpleStringProperty();
            decimalFormat = new DecimalFormat("#.#");
            decimalFormat.setMaximumFractionDigits(Fiat.SMALLEST_UNIT_EXPONENT);
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    public OfferPayload.State getState() {
        return state;
    }

    public void setState(OfferPayload.State state) {
        this.state = state;
        stateProperty().set(state);
    }

    public ObjectProperty<OfferPayload.State> stateProperty() {
        return stateProperty;
    }

    public void resetState() {
        setState(OfferPayload.State.UNDEFINED);
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessageProperty.set(errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Availability
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO refactor those out of OfferPayload, offerPayload should be pure value object
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

    // TODO refactor those out of OfferPayload, offerPayload should be pure value object
    public void cancelAvailabilityRequest() {
        if (availabilityProtocol != null)
            availabilityProtocol.cancel();
    }

    // TODO refactor those out of OfferPayload, offerPayload should be pure value object
    @Nullable
    public Fiat getPrice() {
        if (offerPayload.isUseMarketBasedPrice()) {
            checkNotNull(priceFeedService, "priceFeed must not be null");
            MarketPrice marketPrice = priceFeedService.getMarketPrice(offerPayload.getCurrencyCode());
            if (marketPrice != null) {
                PriceFeedService.Type priceFeedType;
                double factor;
                if (CurrencyUtil.isCryptoCurrency(offerPayload.getCurrencyCode())) {
                    priceFeedType = offerPayload.getDirection() == OfferPayload.Direction.BUY ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
                    factor = offerPayload.getDirection() == OfferPayload.Direction.SELL ? 1 - offerPayload.getMarketPriceMargin() : 1 + offerPayload.getMarketPriceMargin();
                } else {
                    priceFeedType = offerPayload.getDirection() == OfferPayload.Direction.SELL ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
                    factor = offerPayload.getDirection() == OfferPayload.Direction.BUY ? 1 - offerPayload.getMarketPriceMargin() : 1 + offerPayload.getMarketPriceMargin();
                }
                double marketPriceAsDouble = marketPrice.getPrice(priceFeedType);
                double targetPrice = marketPriceAsDouble * factor;
                if (CurrencyUtil.isCryptoCurrency(offerPayload.getCurrencyCode()))
                    targetPrice = targetPrice != 0 ? 1d / targetPrice : 0;
                try {
                    final double rounded = MathUtils.roundDouble(targetPrice, Fiat.SMALLEST_UNIT_EXPONENT);
                    return Fiat.parseFiat(offerPayload.getCurrencyCode(), decimalFormat.format(rounded).replace(",", "."));
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
            return Fiat.valueOf(offerPayload.getCurrencyCode(), offerPayload.getFiatPrice());
        }
    }

    // TODO refactor those out of OfferPayload, offerPayload should be pure value object
    public void checkTradePriceTolerance(long takersTradePrice) throws TradePriceOutOfToleranceException,
            MarketPriceNotAvailableException, IllegalArgumentException {
        checkArgument(takersTradePrice > 0, "takersTradePrice must be positive");
        Fiat tradePriceAsFiat = Fiat.valueOf(offerPayload.getCurrencyCode(), takersTradePrice);
        Fiat offerPriceAsFiat = getPrice();

        if (offerPriceAsFiat == null)
            throw new MarketPriceNotAvailableException("Market price required for calculating trade price is not available.");

        double factor = (double) takersTradePrice / (double) offerPriceAsFiat.value;
        // We allow max. 2 % difference between own offerPayload price calculation and takers calculation.
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

    // TODO
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
        return getVolumeByAmount(offerPayload.getAmount());
    }

    @Nullable
    public Fiat getMinOfferVolume() {
        return getVolumeByAmount(offerPayload.getMinAmount());
    }

    public boolean isMyOffer(KeyRing keyRing) {
        return getPubKeyRing().equals(keyRing.getPubKeyRing());
    }

    /////////////////////////////////// Decorator methods ///////////////////////////////////////////

    public String getShortId() {
        return offerPayload.getShortId();
    }

    public String getId() {
        return offerPayload.getId();
    }

    public OfferPayload.Direction getDirection() {
        return offerPayload.getDirection();
    }

    public String getCurrencyCode() {
        return offerPayload.getCurrencyCode();
    }

    public Coin getMinAmount() {
        return offerPayload.getMinAmount();
    }

    public Coin getAmount() {
        return offerPayload.getAmount();
    }

    public boolean isUseMarketBasedPrice() {
        return offerPayload.isUseMarketBasedPrice();
    }

    public Date getDate() {
        return offerPayload.getDate();
    }

    public double getMarketPriceMargin() {
        return offerPayload.getMarketPriceMargin();
    }

    public PaymentMethod getPaymentMethod() {
        return offerPayload.getPaymentMethod();
    }

    public String getOfferFeePaymentTxID() {
        return offerPayload.getOfferFeePaymentTxID();
    }

    public PubKeyRing getPubKeyRing() {
        return offerPayload.getPubKeyRing();
    }

    public NodeAddress getOffererNodeAddress() {
        return offerPayload.getOffererNodeAddress();
    }

    public String getOffererPaymentAccountId() {
        return offerPayload.getOffererPaymentAccountId();
    }

    public Coin getCreateOfferFee() {
        return offerPayload.getCreateOfferFee();
    }

    public Coin getTxFee() {
        return offerPayload.getTxFee();
    }

    public void setOfferFeePaymentTxID(String offerFeePaymentTxID) {
        offerPayload.setOfferFeePaymentTxID(offerFeePaymentTxID);
    }

    public Coin getBuyerSecurityDeposit() {
        return offerPayload.getBuyerSecurityDeposit();
    }

    public Coin getSellerSecurityDeposit() {
        return offerPayload.getSellerSecurityDeposit();
    }

    public NodeAddress getOwnerNodeAddress() {
        return offerPayload.getOwnerNodeAddress();
    }

    public String getCountryCode() {
        return offerPayload.getCountryCode();
    }

    public String getBankId() {
        return offerPayload.getBankId();
    }

    public List<String> getAcceptedCountryCodes() {
        return offerPayload.getAcceptedCountryCodes();
    }

    public List<String> getAcceptedBankIds() {
        return offerPayload.getAcceptedBankIds();
    }

    public List<NodeAddress> getArbitratorNodeAddresses() {
        return offerPayload.getArbitratorNodeAddresses();
    }

    public OfferPayload.Direction getMirroredDirection() {
        return offerPayload.getMirroredDirection();
    }

    public long getProtocolVersion() {
        return offerPayload.getProtocolVersion();
    }
}
