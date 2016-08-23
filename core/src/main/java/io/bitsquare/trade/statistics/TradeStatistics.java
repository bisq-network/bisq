package io.bitsquare.trade.statistics;

import io.bitsquare.app.Capabilities;
import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.util.JsonExclude;
import io.bitsquare.p2p.storage.payload.CapabilityRequiringPayload;
import io.bitsquare.p2p.storage.payload.LazyProcessedStoragePayload;
import io.bitsquare.p2p.storage.payload.PersistedStoragePayload;
import io.bitsquare.trade.offer.Offer;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.Immutable;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Immutable
public final class TradeStatistics implements LazyProcessedStoragePayload, CapabilityRequiringPayload, PersistedStoragePayload {
    private static final Logger log = LoggerFactory.getLogger(TradeStatistics.class);

    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @JsonExclude
    public static final long TTL = TimeUnit.DAYS.toMillis(30);

    public final String currency;
    public final Offer.Direction direction;
    public final long tradePrice;
    public final long tradeAmount;
    public final long tradeDate;
    public final String paymentMethod;
    public final long offerDate;
    public final boolean useMarketBasedPrice;
    public final double marketPriceMargin;
    public final long offerAmount;
    public final long offerMinAmount;
    public final String offerId;
    public final String depositTxId;
    @JsonExclude
    public final PubKeyRing pubKeyRing;

    public TradeStatistics(Offer offer, Fiat tradePrice, Coin tradeAmount, Date tradeDate, String depositTxId, PubKeyRing pubKeyRing) {
        this.direction = offer.getDirection();
        this.currency = offer.getCurrencyCode();
        this.paymentMethod = offer.getPaymentMethod().getId();
        this.offerDate = offer.getDate().getTime();
        this.useMarketBasedPrice = offer.getUseMarketBasedPrice();
        this.marketPriceMargin = offer.getMarketPriceMargin();
        this.offerAmount = offer.getAmount().value;
        this.offerMinAmount = offer.getMinAmount().value;
        this.offerId = offer.getId();

        this.tradePrice = tradePrice.longValue();
        this.tradeAmount = tradeAmount.value;
        this.tradeDate = tradeDate.getTime();
        this.depositTxId = depositTxId;
        this.pubKeyRing = pubKeyRing;
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
        return Arrays.asList(
                Capabilities.Capability.TRADE_STATISTICS.ordinal()
        );
    }

    public Date getTradeDate() {
        return new Date(tradeDate);
    }

    public Fiat getTradePrice() {
        return Fiat.valueOf(currency, tradePrice);
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Fiat getTradeVolume() {
        return new ExchangeRate(getTradePrice()).coinToFiat(getTradeAmount());
    }

    // We don't include the pubKeyRing as both traders might publish it if the offerer uses an old 
    // version and update later (taker publishes first, then later offerer)
    // We also don't include the trade date as that is set locally and different for offerer and taker
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeStatistics)) return false;

        TradeStatistics that = (TradeStatistics) o;

        if (tradePrice != that.tradePrice) return false;
        if (tradeAmount != that.tradeAmount) return false;
        if (offerDate != that.offerDate) return false;
        if (useMarketBasedPrice != that.useMarketBasedPrice) return false;
        if (Double.compare(that.marketPriceMargin, marketPriceMargin) != 0) return false;
        if (offerAmount != that.offerAmount) return false;
        if (offerMinAmount != that.offerMinAmount) return false;
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) return false;

        if (direction != null && that.direction != null && direction.ordinal() != that.direction.ordinal())
            return false;
        else if ((direction == null && that.direction != null) || (direction != null && that.direction == null))
            return false;

        if (paymentMethod != null ? !paymentMethod.equals(that.paymentMethod) : that.paymentMethod != null)
            return false;
        if (offerId != null ? !offerId.equals(that.offerId) : that.offerId != null) return false;
        return !(depositTxId != null ? !depositTxId.equals(that.depositTxId) : that.depositTxId != null);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = currency != null ? currency.hashCode() : 0;
        result = 31 * result + (direction != null ? direction.ordinal() : 0);
        result = 31 * result + (int) (tradePrice ^ (tradePrice >>> 32));
        result = 31 * result + (int) (tradeAmount ^ (tradeAmount >>> 32));
        result = 31 * result + (paymentMethod != null ? paymentMethod.hashCode() : 0);
        result = 31 * result + (int) (offerDate ^ (offerDate >>> 32));
        result = 31 * result + (useMarketBasedPrice ? 1 : 0);
        temp = Double.doubleToLongBits(marketPriceMargin);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (offerAmount ^ (offerAmount >>> 32));
        result = 31 * result + (int) (offerMinAmount ^ (offerMinAmount >>> 32));
        result = 31 * result + (offerId != null ? offerId.hashCode() : 0);
        result = 31 * result + (depositTxId != null ? depositTxId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TradeStatistics{" +
                "currency='" + currency + '\'' +
                ", direction=" + direction +
                ", tradePrice=" + tradePrice +
                ", tradeAmount=" + tradeAmount +
                ", tradeDate=" + tradeDate +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", offerDate=" + offerDate +
                ", useMarketBasedPrice=" + useMarketBasedPrice +
                ", marketPriceMargin=" + marketPriceMargin +
                ", offerAmount=" + offerAmount +
                ", offerMinAmount=" + offerMinAmount +
                ", offerId='" + offerId + '\'' +
                ", depositTxId='" + depositTxId + '\'' +
                ", pubKeyRing=" + pubKeyRing +
                ", hashCode=" + hashCode() +
                '}';
    }
}
