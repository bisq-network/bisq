package io.bisq.core.offer;

import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.monetary.Altcoin;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.common.util.JsonExclude;
import io.bisq.common.util.MathUtils;
import io.bisq.common.util.Utilities;
import io.bisq.core.exceptions.TradePriceOutOfToleranceException;
import io.bisq.core.offer.availability.OfferAvailabilityModel;
import io.bisq.core.offer.availability.OfferAvailabilityProtocol;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.provider.price.MarketPrice;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Offer implements NetworkPayload, PersistablePayload {

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
    private final OfferPayload offerPayload;
    @JsonExclude
    @Getter
    transient private ObjectProperty<Offer.State> stateProperty = new SimpleObjectProperty<>(Offer.State.UNKNOWN);
    @JsonExclude
    @Nullable
    transient private OfferAvailabilityProtocol availabilityProtocol;
    @JsonExclude
    @Getter
    transient private StringProperty errorMessageProperty = new SimpleStringProperty();
    @JsonExclude
    @Nullable
    @Setter
    transient private PriceFeedService priceFeedService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(OfferPayload offerPayload) {
        this.offerPayload = offerPayload;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.Offer toProtoMessage() {
        return PB.Offer.newBuilder().setOfferPayload(offerPayload.toProtoMessage().getOfferPayload()).build();
    }

    public static Offer fromProto(PB.Offer proto) {
        return new Offer(OfferPayload.fromProto(proto.getOfferPayload()));
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
            if (marketPrice != null && marketPrice.isRecentExternalPriceAvailable()) {
                double factor;
                double marketPriceMargin = offerPayload.getMarketPriceMargin();
                if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                    factor = getDirection() == OfferPayload.Direction.SELL ?
                            1 - marketPriceMargin : 1 + marketPriceMargin;
                } else {
                    factor = getDirection() == OfferPayload.Direction.BUY ?
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
        Price tradePrice = Price.valueOf(getCurrencyCode(), takersTradePrice);
        Price offerPrice = getPrice();
        if (offerPrice == null)
            throw new MarketPriceNotAvailableException("Market price required for calculating trade price is not available.");

        checkArgument(takersTradePrice > 0, "takersTradePrice must be positive");

        double factor = (double) takersTradePrice / (double) offerPrice.getValue();
        // We allow max. 1 % difference between own offerPayload price calculation and takers calculation.
        // Market price might be different at maker's and takers side so we need a bit of tolerance.
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

    public Coin getMakerFee() {
        return Coin.valueOf(offerPayload.getMakerFee());
    }

    public boolean isCurrencyForMakerFeeBtc() {
        return offerPayload.isCurrencyForMakerFeeBtc();
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
        return new PaymentMethod(offerPayload.getPaymentMethodId(),
                offerPayload.getMaxTradePeriod(),
                Coin.valueOf(offerPayload.getMaxTradeLimit()));
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
        return getDirection() == OfferPayload.Direction.BUY;
    }

    public OfferPayload.Direction getMirroredDirection() {
        return getDirection() == OfferPayload.Direction.BUY ? OfferPayload.Direction.SELL : OfferPayload.Direction.BUY;
    }

    public boolean isMyOffer(KeyRing keyRing) {
        return getPubKeyRing().equals(keyRing.getPubKeyRing());
    }


    public Optional<String> getAccountAgeWitnessHashAsHex() {
        if (getExtraDataMap() != null && getExtraDataMap().containsKey(OfferPayload.ACCOUNT_AGE_WITNESS_HASH))
            return Optional.of(getExtraDataMap().get(OfferPayload.ACCOUNT_AGE_WITNESS_HASH));
        else
            return Optional.<String>empty();
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

    public OfferPayload.Direction getDirection() {
        return offerPayload.getDirection();
    }

    public String getId() {
        return offerPayload.getId();
    }

    public List<NodeAddress> getArbitratorNodeAddresses() {
        return offerPayload.getArbitratorNodeAddresses();
    }

    public List<NodeAddress> getMediatorNodeAddresses() {
        return offerPayload.getMediatorNodeAddresses();
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

    public NodeAddress getMakerNodeAddress() {
        return offerPayload.getOwnerNodeAddress();
    }

    public PubKeyRing getPubKeyRing() {
        return offerPayload.getPubKeyRing();
    }

    public String getMakerPaymentAccountId() {
        return offerPayload.getMakerPaymentAccountId();
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
        //noinspection SimplifiableIfStatement
        if (getState() != offer.getState()) return false;
        return !(getErrorMessage() != null ? !getErrorMessage().equals(offer.getErrorMessage()) : offer.getErrorMessage() != null);

    }

    @Override
    public int hashCode() {
        int result = offerPayload != null ? offerPayload.hashCode() : 0;
        result = 31 * result + (getState() != null ? getState().hashCode() : 0);
        result = 31 * result + (getErrorMessage() != null ? getErrorMessage().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Offer{" +
                "getErrorMessage()='" + getErrorMessage() + '\'' +
                ", state=" + getState() +
                ", offerPayload=" + offerPayload +
                '}';
    }
}
