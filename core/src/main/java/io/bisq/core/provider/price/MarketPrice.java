package io.bisq.core.provider.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarketPrice {
    private static final Logger log = LoggerFactory.getLogger(MarketPrice.class);
    public final String currencyCode;
    public final double last;

    public MarketPrice(String currencyCode, double last) {
        this.currencyCode = currencyCode;
        this.last = last;
    }

    public double getPrice() {
        return last;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MarketPrice)) return false;

        MarketPrice that = (MarketPrice) o;

        if (Double.compare(that.last, last) != 0) return false;
        return !(currencyCode != null ? !currencyCode.equals(that.currencyCode) : that.currencyCode != null);

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = currencyCode != null ? currencyCode.hashCode() : 0;
        temp = Double.doubleToLongBits(last);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "MarketPrice{" +
                "currencyCode='" + currencyCode + '\'' +
                ", last='" + last + '\'' +
                '}';
    }
}
