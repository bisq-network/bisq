package io.bisq.offer;

public class MarketPriceNotAvailableException extends Exception {
    public MarketPriceNotAvailableException(String message) {
        super(message);
    }
}
