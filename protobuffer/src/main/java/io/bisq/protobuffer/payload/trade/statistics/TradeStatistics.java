package io.bisq.protobuffer.payload.trade.statistics;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.monetary.Altcoin;
import io.bisq.common.monetary.AltcoinExchangeRate;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.common.util.JsonExclude;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.CapabilityRequiringPayload;
import io.bisq.protobuffer.payload.LazyProcessedStoragePayload;
import io.bisq.protobuffer.payload.PersistedStoragePayload;
import io.bisq.protobuffer.payload.crypto.PubKeyRing;
import io.bisq.protobuffer.payload.offer.OfferPayload;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.TimeUnit;

@ToString
@Slf4j
@Immutable
public final class TradeStatistics implements LazyProcessedStoragePayload, CapabilityRequiringPayload, PersistedStoragePayload {
    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @JsonExclude
    public static final long TTL = TimeUnit.DAYS.toMillis(30);

    // Payload
    public final String baseCurrency;
    public final String counterCurrency;
    public final OfferPayload.Direction direction;
    public final long tradePrice;
    public final long tradeAmount;
    public final long tradeDate;
    public final String paymentMethodId;
    public final long offerDate;
    public final boolean useMarketBasedPrice;
    public final double marketPriceMargin;
    public final long offerAmount;
    public final long offerMinAmount;
    @Getter
    public final String offerId;
    public final String depositTxId;
    @JsonExclude
    public final PubKeyRing pubKeyRing;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Getter
    @Nullable
    private Map<String, String> extraDataMap;

    // Called from domain
    public TradeStatistics(OfferPayload offerPayload,
                           Price tradePrice,
                           Coin tradeAmount,
                           Date tradeDate,
                           String depositTxId,
                           PubKeyRing pubKeyRing) {
        this(offerPayload.getDirection(),
                offerPayload.getBaseCurrencyCode(),
                offerPayload.getCounterCurrencyCode(),
                offerPayload.getPaymentMethodId(),
                offerPayload.getDate(),
                offerPayload.isUseMarketBasedPrice(),
                offerPayload.getMarketPriceMargin(),
                offerPayload.getAmount(),
                offerPayload.getMinAmount(),
                offerPayload.getId(),
                tradePrice.getValue(),
                tradeAmount.value,
                tradeDate.getTime(),
                depositTxId,
                pubKeyRing,
                null);
    }

    // Called from PB
    public TradeStatistics(OfferPayload.Direction direction,
                           String baseCurrency,
                           String counterCurrency,
                           String offerPaymentMethod,
                           long offerDate,
                           boolean offerUseMarketBasedPrice,
                           double offerMarketPriceMargin,
                           long offerAmount,
                           long offerMinAmount,
                           String offerId,
                           long tradePrice,
                           long tradeAmount,
                           long tradeDate,
                           String depositTxId,
                           PubKeyRing pubKeyRing,
                           @Nullable Map<String, String> extraDataMap) {
        this.direction = direction;
        this.baseCurrency = baseCurrency;
        this.counterCurrency = counterCurrency;
        this.paymentMethodId = offerPaymentMethod;
        this.offerDate = offerDate;
        this.useMarketBasedPrice = offerUseMarketBasedPrice;
        this.marketPriceMargin = offerMarketPriceMargin;
        this.offerAmount = offerAmount;
        this.offerMinAmount = offerMinAmount;
        this.offerId = offerId;
        this.tradePrice = tradePrice;
        this.tradeAmount = tradeAmount;
        this.tradeDate = tradeDate;
        this.depositTxId = depositTxId;
        this.pubKeyRing = pubKeyRing;
        this.extraDataMap = extraDataMap;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    @Override
    public List<Integer> getRequiredCapabilities() {
        return Collections.singletonList(
                Capabilities.Capability.TRADE_STATISTICS.ordinal()
        );
    }

    public Date getTradeDate() {
        return new Date(tradeDate);
    }

    public Price getTradePrice() {
        return Price.valueOf(getCurrencyCode(), tradePrice);
    }

    public String getCurrencyCode() {
        return CurrencyUtil.isCryptoCurrency(baseCurrency) ? baseCurrency : counterCurrency;
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Volume getTradeVolume() {
        if (getTradePrice().getMonetary() instanceof Altcoin)
            return new Volume(new AltcoinExchangeRate((Altcoin) getTradePrice().getMonetary()).coinToAltcoin(getTradeAmount()));
        else
            return new Volume(new ExchangeRate((Fiat) getTradePrice().getMonetary()).coinToFiat(getTradeAmount()));
    }

    @Override
    public PB.StoragePayload toProto() {
        final PB.TradeStatistics.Builder builder = PB.TradeStatistics.newBuilder()
                .setTTL(TTL)
                .setBaseCurrency(baseCurrency)
                .setCounterCurrency(counterCurrency)
                .setDirection(PB.OfferPayload.Direction.forNumber(direction.ordinal()))
                .setTradePrice(tradePrice)
                .setTradeAmount(tradeAmount)
                .setTradeDate(tradeDate)
                .setPaymentMethodId(paymentMethodId)
                .setOfferDate(offerDate)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setOfferAmount(offerAmount)
                .setOfferMinAmount(offerMinAmount)
                .setOfferId(offerId)
                .setDepositTxId(depositTxId)
                .setPubKeyRing(pubKeyRing.toProto());
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraDataMap);
        return PB.StoragePayload.newBuilder().setTradeStatistics(builder).build();
    }


    // We don't include the pubKeyRing as both traders might publish it if the offerer uses an old
    // version and update later (taker publishes first, then later offerer)
    // We also don't include the trade date as that is set locally and different for offerer and taker
    @Override
    public int hashCode() {
        int result;
        long temp;
        result = baseCurrency != null ? baseCurrency.hashCode() : 0;
        result = 31 * result + (counterCurrency != null ? counterCurrency.hashCode() : 0);
        result = 31 * result + (direction != null ? direction.hashCode() : 0);
        result = 31 * result + (int) (tradePrice ^ (tradePrice >>> 32));
        result = 31 * result + (int) (tradeAmount ^ (tradeAmount >>> 32));
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (int) (offerDate ^ (offerDate >>> 32));
        result = 31 * result + (useMarketBasedPrice ? 1 : 0);
        temp = Double.doubleToLongBits(marketPriceMargin);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (offerAmount ^ (offerAmount >>> 32));
        result = 31 * result + (int) (offerMinAmount ^ (offerMinAmount >>> 32));
        result = 31 * result + (offerId != null ? offerId.hashCode() : 0);
        result = 31 * result + (depositTxId != null ? depositTxId.hashCode() : 0);
        result = 31 * result + (extraDataMap != null ? extraDataMap.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TradeStatistics that = (TradeStatistics) o;

        if (tradePrice != that.tradePrice) return false;
        if (tradeAmount != that.tradeAmount) return false;
        if (offerDate != that.offerDate) return false;
        if (useMarketBasedPrice != that.useMarketBasedPrice) return false;
        if (Double.compare(that.marketPriceMargin, marketPriceMargin) != 0) return false;
        if (offerAmount != that.offerAmount) return false;
        if (offerMinAmount != that.offerMinAmount) return false;
        if (baseCurrency != null ? !baseCurrency.equals(that.baseCurrency) : that.baseCurrency != null) return false;
        if (counterCurrency != null ? !counterCurrency.equals(that.counterCurrency) : that.counterCurrency != null)
            return false;
        if (direction != that.direction) return false;
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null)
            return false;
        if (offerId != null ? !offerId.equals(that.offerId) : that.offerId != null) return false;
        if (depositTxId != null ? !depositTxId.equals(that.depositTxId) : that.depositTxId != null) return false;
        return !(extraDataMap != null ? !extraDataMap.equals(that.extraDataMap) : that.extraDataMap != null);

    }
}
