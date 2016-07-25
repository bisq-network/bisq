package io.bitsquare.trade;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.util.JsonExclude;
import io.bitsquare.p2p.storage.payload.CapabilityRequiringPayload;
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

public final class TradeStatistics implements StoragePayload, CapabilityRequiringPayload {
    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @JsonExclude
    public static final long TTL = TimeUnit.DAYS.toMillis(10);

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
                Version.Capability.TRADE_STATISTICS.ordinal()
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
        if (getTradeAmount() != null && getTradePrice() != null)
            return new ExchangeRate(getTradePrice()).coinToFiat(getTradeAmount());
        else
            return null;
    }
}
