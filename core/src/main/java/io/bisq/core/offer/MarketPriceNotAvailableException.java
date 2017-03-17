package io.bisq.core.offer;

public class MarketPriceNotAvailableException extends Exception {
    public MarketPriceNotAvailableException(String message) {
        super(message);
    }
}
