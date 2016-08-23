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

    // Used in Json to provide same formatting/rounding for price
    public String tradePriceDisplayString;
    public String tradeAmountDisplayString;
    public String tradeVolumeDisplayString;
    public String currencyPair;

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
                DecimalFormat decimalFormat = new DecimalFormat("#.#");
                decimalFormat.setMaximumFractionDigits(8);
                final double value = tradePriceAsFiat.value != 0 ? 10000D / tradePriceAsFiat.value : 0;
                tradePriceDisplayString = decimalFormat.format(MathUtils.roundDouble(value, 8)).replace(",", ".");
                currencyPair = currency + "/" + "BTC";
            } else {
                tradePriceDisplayString = fiatFormat.noCode().format(tradePriceAsFiat).toString();
                currencyPair = "BTC/" + currency;
            }
            tradeAmountDisplayString = coinFormat.noCode().format(getTradeAmount()).toString();
            tradeVolumeDisplayString = fiatFormat.noCode().format(getTradeVolume()).toString();

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
        if (tradeAmountDisplayString != null ? !tradeAmountDisplayString.equals(that.tradeAmountDisplayString) : that.tradeAmountDisplayString != null)
            return false;
        if (tradeVolumeDisplayString != null ? !tradeVolumeDisplayString.equals(that.tradeVolumeDisplayString) : that.tradeVolumeDisplayString != null)
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
        result = 31 * result + (tradeAmountDisplayString != null ? tradeAmountDisplayString.hashCode() : 0);
        result = 31 * result + (tradeVolumeDisplayString != null ? tradeVolumeDisplayString.hashCode() : 0);
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
}
