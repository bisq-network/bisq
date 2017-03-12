package io.bitsquare.messages.trade.statistics.payload;

import io.bitsquare.app.Capabilities;
import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.util.JsonExclude;
import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.messages.trade.offer.payload.Offer;
import io.bitsquare.p2p.storage.payload.CapabilityRequiringPayload;
import io.bitsquare.p2p.storage.payload.LazyProcessedStoragePayload;
import io.bitsquare.p2p.storage.payload.PersistedStoragePayload;
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
        this(offer.getDirection(), offer.getCurrencyCode(), offer.getPaymentMethod().getId(), offer.getDate().getTime()
                , offer.getUseMarketBasedPrice(), offer.getMarketPriceMargin(), offer.getAmount().value,
                offer.getMinAmount().value, offer.getId(), tradePrice.longValue(), tradeAmount.value,
                tradeDate.getTime(), depositTxId, pubKeyRing);
    }

    public TradeStatistics(Offer.Direction direction, String offerCurrency, String offerPaymentMethod,
                           long offerDate, boolean offerUseMarketBasedPrice, double offerMarketPriceMargin,
                           long offerAmount, long offerMinAmount, String offerId, long tradePrice, long tradeAmount,
                           long tradeDate,
                           String depositTxId, PubKeyRing pubKeyRing) {
        this.direction = direction;
        this.currency = offerCurrency;
        this.paymentMethod = offerPaymentMethod;
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

    public String getOfferId() {
        return offerId;
    }

    @Override
    public Messages.StoragePayload toProtoBuf() {
        return Messages.StoragePayload.newBuilder().setTradeStatistics(Messages.TradeStatistics.newBuilder()
                .setTTL(TTL)
                .setCurrency(currency)
                .setDirection(Messages.Offer.Direction.forNumber(direction.ordinal()))
                .setTradePrice(tradePrice)
                .setTradeAmount(tradeAmount)
                .setTradeDate(tradeDate)
                .setPaymentMethod(paymentMethod)
                .setOfferDate(offerDate)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setOfferAmount(offerAmount)
                .setOfferMinAmount(offerMinAmount)
                .setOfferId(offerId)
                .setDepositTxId(depositTxId)
                .setPubKeyRing((Messages.PubKeyRing) pubKeyRing.toProtoBuf())).build();
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
        if (getOfferId() != null ? !getOfferId().equals(that.getOfferId()) : that.getOfferId() != null) return false;
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
        result = 31 * result + (getOfferId() != null ? getOfferId().hashCode() : 0);
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
                ", offerId='" + getOfferId() + '\'' +
                ", depositTxId='" + depositTxId + '\'' +
                ", pubKeyRing=" + pubKeyRing +
                ", hashCode=" + hashCode() +
                '}';
    }
}
