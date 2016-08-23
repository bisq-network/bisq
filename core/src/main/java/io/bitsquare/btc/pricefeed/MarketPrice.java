package io.bitsquare.btc.pricefeed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Double.parseDouble;

public class MarketPrice {
    private static final Logger log = LoggerFactory.getLogger(MarketPrice.class);
    public final String currencyCode;
    private final double ask;
    private final double bid;
    private final double last;

    public MarketPrice(String currencyCode, String ask, String bid, String last) {
        this.currencyCode = currencyCode;
        this.ask = parseDouble(ask);
        this.bid = parseDouble(bid);
        this.last = parseDouble(last);
    }

    public double getPrice(PriceFeedService.Type type) {
        switch (type) {
            case ASK:
                return ask;
            case BID:
                return (bid);
            case LAST:
                return last;
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MarketPrice)) return false;

        MarketPrice that = (MarketPrice) o;

        if (Double.compare(that.ask, ask) != 0) return false;
        if (Double.compare(that.bid, bid) != 0) return false;
        if (Double.compare(that.last, last) != 0) return false;
        return !(currencyCode != null ? !currencyCode.equals(that.currencyCode) : that.currencyCode != null);

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = currencyCode != null ? currencyCode.hashCode() : 0;
        temp = Double.doubleToLongBits(ask);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(bid);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(last);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "MarketPrice{" +
                "currencyCode='" + currencyCode + '\'' +
                ", ask='" + ask + '\'' +
                ", bid='" + bid + '\'' +
                ", last='" + last + '\'' +
                '}';
    }
}
