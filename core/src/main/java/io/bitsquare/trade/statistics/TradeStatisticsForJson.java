package io.bitsquare.trade.statistics;

import io.bitsquare.common.util.MathUtils;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.trade.offer.Offer;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.Immutable;
import java.text.DecimalFormat;

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

    public String tradePriceDisplayString;

    public String primaryMarketTradeAmountDisplayString;
    public String primaryMarketTradeVolumeDisplayString;

    public long primaryMarketTradePrice;
    public long primaryMarketTradeAmount;
    public long primaryMarketTradeVolume;


    public TradeStatisticsForJson(TradeStatistics tradeStatistics) {
        this.direction = tradeStatistics.direction;
        this.currency = tradeStatistics.currency;
        this.paymentMethod = tradeStatistics.paymentMethod;
        this.offerDate = tradeStatistics.offerDate;
        this.useMarketBasedPrice = tradeStatistics.useMarketBasedPrice;
        this.marketPriceMargin = tradeStatistics.marketPriceMargin;
        this.offerAmount = tradeStatistics.offerAmount;
        this.offerMinAmount = tradeStatistics.offerMinAmount;
        this.offerId = tradeStatistics.offerId;
        this.tradePrice = tradeStatistics.tradePrice;
        this.tradeAmount = tradeStatistics.tradeAmount;
        this.tradeDate = tradeStatistics.tradeDate;
        this.depositTxId = tradeStatistics.depositTxId;


        try {
            MonetaryFormat fiatFormat = MonetaryFormat.FIAT.repeatOptionalDecimals(0, 0);
            MonetaryFormat coinFormat = MonetaryFormat.BTC.minDecimals(2).repeatOptionalDecimals(1, 6);
            final Fiat tradePriceAsFiat = getTradePrice();
            if (CurrencyUtil.isCryptoCurrency(currency)) {
                primaryMarketDirection = direction == Offer.Direction.BUY ? Offer.Direction.SELL : Offer.Direction.BUY;
                final double value = tradePriceAsFiat.value != 0 ? 10000D / tradePriceAsFiat.value : 0;
                DecimalFormat decimalFormat = new DecimalFormat("#.#");
                decimalFormat.setMaximumFractionDigits(8);
                tradePriceDisplayString = decimalFormat.format(MathUtils.roundDouble(value, 8)).replace(",", ".");
                currencyPair = currency + "/" + "BTC";

                primaryMarketTradePrice = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(value, 8));

                primaryMarketTradeVolumeDisplayString = coinFormat.noCode().format(getTradeAmount()).toString();
                primaryMarketTradeAmountDisplayString = fiatFormat.noCode().format(getTradeVolume()).toString();

                primaryMarketTradeAmount = (long) MathUtils.scaleUpByPowerOf10(getTradeVolume().longValue(), 4);
                primaryMarketTradeVolume = getTradeAmount().longValue();
            } else {
                primaryMarketDirection = direction;
                currencyPair = "BTC/" + currency;
                tradePriceDisplayString = fiatFormat.noCode().format(tradePriceAsFiat).toString();

                primaryMarketTradePrice = (long) MathUtils.scaleUpByPowerOf10(tradePriceAsFiat.longValue(), 4);

                primaryMarketTradeAmountDisplayString = coinFormat.noCode().format(getTradeAmount()).toString();
                primaryMarketTradeVolumeDisplayString = fiatFormat.noCode().format(getTradeVolume()).toString();

                primaryMarketTradeAmount = getTradeAmount().longValue();
                primaryMarketTradeVolume = (long) MathUtils.scaleUpByPowerOf10(getTradeVolume().longValue(), 4);
            }
        } catch (Throwable t) {
            log.error("Error at setDisplayStrings: " + t.getMessage());
        }
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
        if (tradePriceDisplayString != null ? !tradePriceDisplayString.equals(that.tradePriceDisplayString) : that.tradePriceDisplayString != null)
            return false;
        if (primaryMarketTradeAmountDisplayString != null ? !primaryMarketTradeAmountDisplayString.equals(that.primaryMarketTradeAmountDisplayString) : that.primaryMarketTradeAmountDisplayString != null)
            return false;
        if (primaryMarketTradeVolumeDisplayString != null ? !primaryMarketTradeVolumeDisplayString.equals(that.primaryMarketTradeVolumeDisplayString) : that.primaryMarketTradeVolumeDisplayString != null)
            return false;
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
        result = tradePriceDisplayString != null ? tradePriceDisplayString.hashCode() : 0;
        result = 31 * result + (primaryMarketTradeAmountDisplayString != null ? primaryMarketTradeAmountDisplayString.hashCode() : 0);
        result = 31 * result + (primaryMarketTradeVolumeDisplayString != null ? primaryMarketTradeVolumeDisplayString.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
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
                ", tradePriceDisplayString='" + tradePriceDisplayString + '\'' +
                ", tradeAmountDisplayString='" + primaryMarketTradeAmountDisplayString + '\'' +
                ", tradeVolumeDisplayString='" + primaryMarketTradeVolumeDisplayString + '\'' +
                ", currencyPair='" + currencyPair + '\'' +
                ", primaryMarketTradeAmount=" + primaryMarketTradeAmount +
                ", primaryMarketTradeVolume=" + primaryMarketTradeVolume +
                '}';
    }
}
