package io.bisq.core.offer;

import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.util.JsonExclude;
import io.bisq.common.util.MathUtils;
import io.bisq.common.util.Utilities;
import io.bisq.core.exceptions.TradePriceOutOfToleranceException;
import io.bisq.core.offer.availability.OfferAvailabilityModel;
import io.bisq.core.offer.availability.OfferAvailabilityProtocol;
import io.bisq.core.provider.price.MarketPrice;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.wire.crypto.KeyRing;
import io.bisq.wire.payload.crypto.PubKeyRing;
import io.bisq.wire.payload.offer.OfferPayload;
import io.bisq.wire.payload.p2p.NodeAddress;
import io.bisq.wire.payload.payment.PaymentMethod;
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
import java.security.PublicKey;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Offer implements Serializable {

    @Getter
    private final OfferPayload offerPayload;
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
    @Setter
    @Nullable
    transient private PriceFeedService priceFeedService;
    @JsonExclude
    transient private DecimalFormat decimalFormat;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(OfferPayload offerPayload) {
        this.offerPayload = offerPayload;

        // we don't need to fill it as the error message is only relevant locally, so we don't store it in the transmitted object
        decimalFormat = new DecimalFormat("#.#");
        decimalFormat.setMaximumFractionDigits(Fiat.SMALLEST_UNIT_EXPONENT);
    }

    // TODO still needed as we get the offer from persistence serialized
    // can be removed once we have full PB support
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
    public Fiat getPrice() {
        String currencyCode = getCurrencyCode();
        if (offerPayload.isUseMarketBasedPrice()) {
            checkNotNull(priceFeedService, "priceFeed must not be null");
            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
            if (marketPrice != null) {
                PriceFeedService.Type priceFeedType;
                double factor;
                double marketPriceMargin = offerPayload.getMarketPriceMargin();
                if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                    priceFeedType = getDirection() == OfferPayload.Direction.BUY ?
                            PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
                    factor = getDirection() == OfferPayload.Direction.SELL ?
                            1 - marketPriceMargin : 1 + marketPriceMargin;
                } else {
                    priceFeedType = getDirection() == OfferPayload.Direction.SELL ?
                            PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
                    factor = getDirection() == OfferPayload.Direction.BUY ?
                            1 - marketPriceMargin : 1 + marketPriceMargin;
                }
                double marketPriceAsDouble = marketPrice.getPrice(priceFeedType);
                double targetPrice = marketPriceAsDouble * factor;
                if (CurrencyUtil.isCryptoCurrency(currencyCode))
                    targetPrice = targetPrice != 0 ? 1d / targetPrice : 0;
                try {
                    final double rounded = MathUtils.roundDouble(targetPrice, Fiat.SMALLEST_UNIT_EXPONENT);
                    return Fiat.parseFiat(currencyCode,
                            decimalFormat.format(rounded).replace(",", "."));
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
            return Fiat.valueOf(currencyCode, offerPayload.getFiatPrice());
        }
    }

    // TODO refactor those out of OfferPayload, offerPayload should be pure value object
    public void checkTradePriceTolerance(long takersTradePrice) throws TradePriceOutOfToleranceException,
            MarketPriceNotAvailableException, IllegalArgumentException {
        checkArgument(takersTradePrice > 0, "takersTradePrice must be positive");
        Fiat tradePriceAsFiat = Fiat.valueOf(getCurrencyCode(), takersTradePrice);
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

    public void resetState() {
        setState(OfferPayload.State.UNDEFINED);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setState(OfferPayload.State state) {
        this.state = state;
        stateProperty().set(state);
    }

    public ObjectProperty<OfferPayload.State> stateProperty() {
        return stateProperty;
    }

    public void setOfferFeePaymentTxID(String offerFeePaymentTxID) {
        offerPayload.setOfferFeePaymentTxID(offerFeePaymentTxID);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isMyOffer(KeyRing keyRing) {
        return getPubKeyRing().equals(keyRing.getPubKeyRing());
    }

    @Nullable
    public Fiat getOfferVolume() {
        return getVolumeByAmount(getAmount());
    }

    @Nullable
    public Fiat getMinOfferVolume() {
        return getVolumeByAmount(getMinAmount());
    }

    public OfferPayload.State getState() {
        return state;
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessageProperty.set(errorMessage);
    }

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

    public String getShortId() {
        return Utilities.getShortId(offerPayload.getId());
    }

    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.getPaymentMethodById(offerPayload.getPaymentMethodId());
    }

    public OfferPayload.Direction getMirroredDirection() {
        return getDirection() == OfferPayload.Direction.BUY ? OfferPayload.Direction.SELL : OfferPayload.Direction.BUY;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegate Getter (boilerplate code generated via IntelliJ generate delegte feature)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferPayload.Direction getDirection() {
        return offerPayload.getDirection();
    }

    @Nullable
    public Map<String, String> getExtraDataMap() {
        return offerPayload.getExtraDataMap();
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
        return offerPayload.getCurrencyCode();
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

    public String getOfferFeePaymentTxID() {
        return offerPayload.getOfferFeePaymentTxID();
    }

    public String getVersionNr() {
        return offerPayload.getVersionNr();
    }

    public long getBlockHeightAtOfferCreation() {
        return offerPayload.getBlockHeightAtOfferCreation();
    }

    public long getMaxTradePeriod() {
        return offerPayload.getMaxTradePeriod();
    }

    public boolean isUseAutoClose() {
        return offerPayload.isUseAutoClose();
    }

    public NodeAddress getOwnerNodeAddress() {
        return offerPayload.getOwnerNodeAddress();
    }

    public PublicKey getOwnerPubKey() {
        return offerPayload.getOwnerPubKey();
    }
}
