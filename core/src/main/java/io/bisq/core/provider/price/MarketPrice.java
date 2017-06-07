package io.bisq.core.provider.price;

import lombok.Value;

import java.time.Instant;

@Value
public class MarketPrice {
    private static final long MARKET_PRICE_MAX_AGE_SEC = 1800;  // 30 min

    private final String currencyCode;
    private final double price;
    private final long timestampSec;

    public MarketPrice(String currencyCode, double price, long timestampSec) {
        this.currencyCode = currencyCode;
        this.price = price;
        this.timestampSec = timestampSec;
    }

    public boolean isValid() {
        long limit = Instant.now().getEpochSecond() - MARKET_PRICE_MAX_AGE_SEC;
        return timestampSec > limit && price > 0;
    }
}
