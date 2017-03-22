package io.bisq.core.trade.statistics;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.common.util.MathUtils;
import io.bisq.core.offer.Offer;
import io.bisq.protobuffer.payload.trade.statistics.TradeStatistics;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.Immutable;

@Immutable
public final class TradeStatisticsForJson {
    private static final Logger log = LoggerFactory.getLogger(TradeStatisticsForJson.class);

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

    // primaryMarket fields are based on industry standard where primaryMarket is always in the focus (in the app BTC is always in the focus - will be changed in a larger refactoring once)
    public String currencyPair;
    public Offer.Direction primaryMarketDirection;

    public long primaryMarketTradePrice;
    public long primaryMarketTradeAmount;
    public long primaryMarketTradeVolume;


    public TradeStatisticsForJson(TradeStatistics tradeStatistics) {
        this.direction = Offer.Direction.valueOf(tradeStatistics.direction.name());
        this.currency = tradeStatistics.getCurrencyCode();
        this.paymentMethod = tradeStatistics.paymentMethodId;
        this.offerDate = tradeStatistics.offerDate;
        this.useMarketBasedPrice = tradeStatistics.useMarketBasedPrice;
        this.marketPriceMargin = tradeStatistics.marketPriceMargin;
        this.offerAmount = tradeStatistics.offerAmount;
        this.offerMinAmount = tradeStatistics.offerMinAmount;
        this.offerId = tradeStatistics.getOfferId();
        this.tradePrice = tradeStatistics.tradePrice;
        this.tradeAmount = tradeStatistics.tradeAmount;
        this.tradeDate = tradeStatistics.tradeDate;
        this.depositTxId = tradeStatistics.depositTxId;


        try {
            final Price tradePrice = getTradePrice();
            if (CurrencyUtil.isCryptoCurrency(currency)) {
                primaryMarketDirection = direction == Offer.Direction.BUY ? Offer.Direction.SELL : Offer.Direction.BUY;
                currencyPair = currency + "/" + "BTC";

                primaryMarketTradePrice = tradePrice.getValue();

                primaryMarketTradeAmount = getTradeVolume() != null ? getTradeVolume().getValue() : 0;
                primaryMarketTradeVolume = getTradeAmount().getValue();
            } else {
                primaryMarketDirection = direction;
                currencyPair = "BTC/" + currency;

                // we use precision 4 for fiat based price but on the markets api we use precision 8 so we scale up by 10000
                primaryMarketTradePrice = (long) MathUtils.scaleUpByPowerOf10(tradePrice.getValue(), 4);

                primaryMarketTradeAmount = getTradeAmount().getValue();
                // we use precision 4 for fiat but on the markets api we use precision 8 so we scale up by 10000
                primaryMarketTradeVolume = getTradeVolume() != null ?
                        (long) MathUtils.scaleUpByPowerOf10(getTradeVolume().getValue(), 4) : 0;
            }
        } catch (Throwable t) {
            log.error("Error at setDisplayStrings: " + t.getMessage());
            t.printStackTrace();
        }
    }


    public Price getTradePrice() {
        return Price.valueOf(currency, tradePrice);
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Volume getTradeVolume() {
        return getTradePrice().getVolumeByAmount(getTradeAmount());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeStatisticsForJson)) return false;

        TradeStatisticsForJson that = (TradeStatisticsForJson) o;

        if (tradePrice != that.tradePrice) return false;
        if (tradeAmount != that.tradeAmount) return false;
        if (tradeDate != that.tradeDate) return false;
        if (offerDate != that.offerDate) return false;
        if (useMarketBasedPrice != that.useMarketBasedPrice) return false;
        if (Double.compare(that.marketPriceMargin, marketPriceMargin) != 0) return false;
        if (offerAmount != that.offerAmount) return false;
        if (offerMinAmount != that.offerMinAmount) return false;
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) return false;
        if (direction != that.direction) return false;
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
        result = 31 * result + (direction != null ? direction.hashCode() : 0);
        result = 31 * result + (int) (tradePrice ^ (tradePrice >>> 32));
        result = 31 * result + (int) (tradeAmount ^ (tradeAmount >>> 32));
        result = 31 * result + (int) (tradeDate ^ (tradeDate >>> 32));
        result = 31 * result + (paymentMethod != null ? paymentMethod.hashCode() : 0);
        result = 31 * result + (int) (offerDate ^ (offerDate >>> 32));
        result = 31 * result + (useMarketBasedPrice ? 1 : 0);
        temp = Double.doubleToLongBits(marketPriceMargin);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (offerAmount ^ (offerAmount >>> 32));
        result = 31 * result + (int) (offerMinAmount ^ (offerMinAmount >>> 32));
        result = 31 * result + (offerId != null ? offerId.hashCode() : 0);
        result = 31 * result + (depositTxId != null ? depositTxId.hashCode() : 0);
        result = 31 * result + (currencyPair != null ? currencyPair.hashCode() : 0);
        result = 31 * result + (primaryMarketDirection != null ? primaryMarketDirection.hashCode() : 0);
        result = 31 * result + (int) (primaryMarketTradePrice ^ (primaryMarketTradePrice >>> 32));
        result = 31 * result + (int) (primaryMarketTradeAmount ^ (primaryMarketTradeAmount >>> 32));
        result = 31 * result + (int) (primaryMarketTradeVolume ^ (primaryMarketTradeVolume >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "TradeStatisticsForJson{" +
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
                ", currencyPair='" + currencyPair + '\'' +
                ", primaryMarketTradeAmount=" + primaryMarketTradeAmount +
                ", primaryMarketTradeVolume=" + primaryMarketTradeVolume +
                '}';
    }
}
