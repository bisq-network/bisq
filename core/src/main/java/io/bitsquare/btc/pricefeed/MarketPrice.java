package io.bitsquare.btc.pricefeed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarketPrice {
    private static final Logger log = LoggerFactory.getLogger(MarketPrice.class);
    public final String currencyCode;
    public final double ask;
    public final double bid;
    public final double last;

    public MarketPrice(String currencyCode, double ask, double bid, double last) {
        this.currencyCode = currencyCode;
        this.ask = ask;
        this.bid = bid;
        this.last = last;
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
        if (o == null || this.getClass() != o.getClass()) return false;

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
