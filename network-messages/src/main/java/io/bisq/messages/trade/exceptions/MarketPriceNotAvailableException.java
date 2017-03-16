package io.bisq.messages.trade.exceptions;

public class MarketPriceNotAvailableException extends Exception {
    public MarketPriceNotAvailableException(String message) {
        super(message);
    }
}
