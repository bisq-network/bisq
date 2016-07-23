package io.bitsquare.trade;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.p2p.storage.payload.CapabilityAwarePayload;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.trade.offer.Offer;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class TradeStatistics implements StoragePayload, CapabilityAwarePayload {
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    public static final long TTL = TimeUnit.DAYS.toMillis(10);

    public final Offer offer;
    public final long tradePriceAsLong;
    public final long tradeAmountAsLong;
    public final long tradeDateAsTime;
    public final String depositTxId;
    public final byte[] contractHash;
    public final PubKeyRing pubKeyRing;

    public final int protocolVersion;

    public TradeStatistics(Offer offer, Fiat tradePrice, Coin tradeAmount, Date tradeDate, String depositTxId, byte[] contractHash, PubKeyRing pubKeyRing) {
        this.offer = offer;
        this.depositTxId = depositTxId;
        this.tradePriceAsLong = tradePrice.longValue();
        tradeAmountAsLong = tradeAmount.value;
        this.tradeDateAsTime = tradeDate.getTime();
        this.contractHash = contractHash;
        this.pubKeyRing = pubKeyRing;

        protocolVersion = Version.TRADE_PROTOCOL_VERSION;
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
                Version.Capability.TRADE_STATISTICS.ordinal()
        );
    }

    public Date getTradeDate() {
        return new Date(tradeDateAsTime);
    }

    public Fiat getTradePrice() {
        return Fiat.valueOf(offer.getCurrencyCode(), tradePriceAsLong);
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmountAsLong);
    }

    public Fiat getTradeVolume() {
        if (getTradeAmount() != null && getTradePrice() != null)
            return new ExchangeRate(getTradePrice()).coinToFiat(getTradeAmount());
        else
            return null;
    }

    // We compare the objects of both traders to match. 
    // pubKeyRing is not matching so we excluded it
    public boolean isSameTrade(TradeStatistics o) {
        if (this == o) return true;
        if (!(o instanceof TradeStatistics)) return false;

        TradeStatistics that = (TradeStatistics) o;

        if (tradePriceAsLong != that.tradePriceAsLong) return false;
        if (tradeAmountAsLong != that.tradeAmountAsLong) return false;
        if (tradeDateAsTime != that.tradeDateAsTime) return false;
        if (protocolVersion != that.protocolVersion) return false;
        if (offer != null ? !offer.equals(that.offer) : that.offer != null) return false;
        if (depositTxId != null ? !depositTxId.equals(that.depositTxId) : that.depositTxId != null) return false;
        return Arrays.equals(contractHash, that.contractHash);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeStatistics)) return false;

        TradeStatistics that = (TradeStatistics) o;

        if (tradePriceAsLong != that.tradePriceAsLong) return false;
        if (tradeAmountAsLong != that.tradeAmountAsLong) return false;
        if (tradeDateAsTime != that.tradeDateAsTime) return false;
        if (protocolVersion != that.protocolVersion) return false;
        if (offer != null ? !offer.equals(that.offer) : that.offer != null) return false;
        if (depositTxId != null ? !depositTxId.equals(that.depositTxId) : that.depositTxId != null) return false;
        if (!Arrays.equals(contractHash, that.contractHash)) return false;
        return !(pubKeyRing != null ? !pubKeyRing.equals(that.pubKeyRing) : that.pubKeyRing != null);

    }

    @Override
    public int hashCode() {
        int result = offer != null ? offer.hashCode() : 0;
        result = 31 * result + (int) (tradePriceAsLong ^ (tradePriceAsLong >>> 32));
        result = 31 * result + (int) (tradeAmountAsLong ^ (tradeAmountAsLong >>> 32));
        result = 31 * result + (int) (tradeDateAsTime ^ (tradeDateAsTime >>> 32));
        result = 31 * result + (depositTxId != null ? depositTxId.hashCode() : 0);
        result = 31 * result + (contractHash != null ? Arrays.hashCode(contractHash) : 0);
        result = 31 * result + (pubKeyRing != null ? pubKeyRing.hashCode() : 0);
        result = 31 * result + protocolVersion;
        return result;
    }

    @Override
    public String toString() {
        return "TradeStatistics{" +
                "offer=" + offer +
                ", tradePriceAsLong=" + tradePriceAsLong +
                ", tradeAmountAsLong=" + tradeAmountAsLong +
                ", tradeDateAsTime=" + tradeDateAsTime +
                ", depositTxId='" + depositTxId + '\'' +
                ", contractHash=" + Arrays.toString(contractHash) +
                ", pubKeyRing=" + pubKeyRing +
                ", protocolVersion=" + protocolVersion +
                '}';
    }

}
